package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.StudentService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.StudentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StudentController 切片测试（Controller 层）。
 * <p>
 * <b>为什么用切片，而不是直接 new 一个 Controller 调方法：</b>
 * Controller 的价值有一半在方法体之外——路由映射、参数绑定、@Valid 校验、
 * @PreAuthorize 权限、异常到 HTTP 状态码的翻译。这些全靠 Spring 的代理和过滤器链，
 * 直接调方法它们全部不生效，测了等于没测。
 * <p>
 * <b>为什么不用 standaloneMockMvc：</b>standalone 不加载 Spring 容器，
 * 没有 @EnableMethodSecurity 的 AOP 代理，@PreAuthorize 形同虚设，
 * "学生访问管理员接口应该 403"这条断言会假绿。
 * <p>
 * 所以这里加载真实的 SecurityConfig + 真实的 JwtAuthenticationFilter + 真实的 JwtUtil，
 * 用真实签发的 JWT 走完整条链路。代价是每个测试类多约 2 秒启动时间，
 * 收益是权限矩阵这个最容易写错、也最危险的部分被真实覆盖。
 */
@WebMvcTest(controllers = StudentController.class)
@ContextConfiguration(classes = StudentControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class StudentControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    /** 学号规则是 S + 9 位数字（见 RegisterRequestDTO 的 @Pattern） */
    private static final String STUDENT_NO = "S202300001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private StudentService studentService;

    /**
     * 黑名单服务是 Redis 依赖，切片里必须打桩，否则每个请求都要连 Redis。
     * 默认放行（未登出、未改密），需要拦截的用例单独覆写。
     */
    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void allowTokensByDefault() {
        when(tokenBlacklistService.isRevoked(any())).thenReturn(false);
        when(tokenBlacklistService.isStaleByPasswordChange(anyLong(), any(Instant.class))).thenReturn(false);
    }

    private String token(long userId, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, "测试用户", role);
    }

    private StudentResponse sampleStudent() {
        return StudentResponse.builder()
                .id(STUDENT_ID)
                .studentNo(STUDENT_NO)
                .name("张三")
                .gender(0)
                .age(20)
                .phone("13800138000")
                .build();
    }

    // ==================== 认证 ====================

    @Test
    @DisplayName("登录成功：200 + 返回 token")
    void login_success() throws Exception {
        when(studentService.login(any())).thenReturn(
                LoginResponseDTO.builder().id(STUDENT_ID).studentNo(STUDENT_NO)
                        .name("张三").token("fake-token").build());

        mockMvc.perform(post("/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + STUDENT_NO + "\",\"password\":\"Abc12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("fake-token"));
    }

    @Test
    @DisplayName("登录：密码错误 → 401，且 HTTP 状态与 body.code 一致")
    void login_badCredentials() throws Exception {
        when(studentService.login(any())).thenThrow(new BusinessException(401, "学号或密码错误"));

        mockMvc.perform(post("/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"" + STUDENT_NO + "\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录：学号格式不合法 → 400，且不调用 Service（@Valid 在业务层之前拦住）")
    void login_invalidStudentNo() throws Exception {
        mockMvc.perform(post("/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"abc\",\"password\":\"Abc12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(studentService);
    }

    // ==================== 权限矩阵 ====================

    @Test
    @DisplayName("未带 token 访问受保护接口 → 401（不是 403）")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/student"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("学生访问学生列表 → 403（列表只给教师/管理员）")
    void student_cannotList() throws Exception {
        mockMvc.perform(get("/student").header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(studentService);
    }

    @Test
    @DisplayName("教师访问学生列表 → 200")
    void teacher_canList() throws Exception {
        when(studentService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 10L));

        mockMvc.perform(get("/student").header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("学生查自己的详情 → 200")
    void student_canReadSelf() throws Exception {
        when(studentService.getByIdForCurrentUser(STUDENT_ID)).thenReturn(sampleStudent());

        mockMvc.perform(get("/student/" + STUDENT_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(STUDENT_ID))
                .andExpect(jsonPath("$.data.name").value("张三"));
    }

    @Test
    @DisplayName("学生查别人的详情 → 403（防遍历 id 拉全校资料）")
    void student_cannotReadOthers() throws Exception {
        when(studentService.getByIdForCurrentUser(99L))
                .thenThrow(new BusinessException(403, "只能查看自己的信息"));

        mockMvc.perform(get("/student/99")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("学生删除学生 → 403（写操作一律教师/管理员）")
    void student_cannotDelete() throws Exception {
        mockMvc.perform(delete("/student/" + STUDENT_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(studentService);
    }

    @Test
    @DisplayName("被登出的 token → 401（黑名单生效，且不进入业务层）")
    void revokedToken_returns401() throws Exception {
        when(tokenBlacklistService.isRevoked(any())).thenReturn(true);

        mockMvc.perform(get("/student").header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(studentService);
    }

    // ==================== 响应体不泄露密码 ====================

    @Test
    @DisplayName("学生详情响应里不含 password 字段（VO 保证，不是靠 @JsonIgnore 遮盖）")
    void response_hasNoPasswordField() throws Exception {
        when(studentService.getByIdForCurrentUser(STUDENT_ID)).thenReturn(sampleStudent());

        mockMvc.perform(get("/student/" + STUDENT_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.studentNo").value(STUDENT_NO));
    }

    // ==================== 业务异常 → HTTP 状态码 ====================

    @Test
    @DisplayName("查不存在的学生 → 404")
    void getById_notFound() throws Exception {
        when(studentService.getByIdForCurrentUser(9999L))
                .thenThrow(new BusinessException(404, "学生不存在"));

        mockMvc.perform(get("/student/9999")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("路径参数类型不对 → 400（不能变成 500）")
    void getById_typeMismatch() throws Exception {
        mockMvc.perform(get("/student/abc")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("重置密码：密码太短 → 400，且不调用 Service")
    void resetPassword_invalid() throws Exception {
        mockMvc.perform(post("/student/" + STUDENT_ID + "/reset-password")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest());

        verify(studentService, never()).resetPassword(anyLong(), any());
    }

    @Test
    @DisplayName("重置密码成功 → 200，Service 收到路径 id 与请求体新密码")
    void resetPassword_success() throws Exception {
        mockMvc.perform(post("/student/" + STUDENT_ID + "/reset-password")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPass123\"}"))
                .andExpect(status().isOk());

        verify(studentService).resetPassword(eq(STUDENT_ID), eq("NewPass123"));
    }

    /**
     * 切片测试的显式配置。
     * <p>
     * <b>为什么需要换掉配置来源：</b>{@code @WebMvcTest} 默认把
     * {@code @SpringBootApplication} 启动类当作配置来源，而启动类上挂着
     * {@code @MapperScan("com.example.demo.mapper")}。于是 6 个 Mapper 接口全被注册成 bean，
     * 切片里又没有 {@code SqlSessionFactory}（MyBatis 自动配置提供，切片不加载），
     * 启动直接报 {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
     * 用 {@code @ContextConfiguration(classes = SliceConfig.class)} 换掉配置来源，
     * 启动类不参与，MapperScan 就不会执行——比给 6 个 Mapper 逐个打桩干净。
     * <p>
     * <b>为什么必须 @EnableWebSecurity：</b>切片不启用 Spring Security 的 Web 基础设施，
     * 容器里没有 {@code HttpSecurity}，{@code SecurityConfig#securityFilterChain} 会报
     * {@code No qualifying bean of type 'HttpSecurity'}。加这个注解才会导入
     * {@code HttpSecurityConfiguration} 提供该 bean。注意不能用
     * {@code @MockitoBean HttpSecurity} 糊弄——那样 filterChain 里的
     * {@code .csrf()/.authorizeHttpRequests()} 全作用在 mock 上，{@code http.build()}
     * 返回空壳，安全规则一条都不生效。
     * <p>
     * <b>为什么要把 Controller 和 GlobalExceptionHandler 也 import：</b>
     * 换掉配置来源后组件扫描不再执行，Controller bean 与
     * {@code @RestControllerAdvice} 都得显式引入，否则所有请求 404、所有业务异常变成 500。
     */
    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            StudentController.class
    })
    static class SliceConfig {

        @Bean
        JwtUtil jwtUtil() {
            JwtUtil util = new JwtUtil();
            ReflectionTestUtils.setField(util, "secret",
                    "Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=");
            ReflectionTestUtils.setField(util, "expiration", 7200000L);
            return util;
        }
    }
}

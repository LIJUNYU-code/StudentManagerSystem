package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.ClazzService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.ClazzResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ClazzController 切片测试。
 * <p>
 * 关注点：读接口对全体登录用户开放（学生选课时要能看到班级），
 * 写接口只给教师/管理员。这个"读放开、写收紧"的不对称是有意的，
 * 一旦被改成一律 authenticated，学生端会静默失效（页面空白而不是报错）。
 */
@WebMvcTest(controllers = ClazzController.class)
@ContextConfiguration(classes = ClazzControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class ClazzControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long CLAZZ_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private ClazzService clazzService;

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

    private ClazzResponse sampleClazz() {
        return ClazzResponse.builder()
                .id(CLAZZ_ID).name("软件工程1班").grade("2023")
                // 注意：ClazzResponse.teacherId 是 Integer（历史类型），不能直接传 long 常量
                .teacherId((int) TEACHER_ID).build();
    }

    // ==================== 读：登录即可 ====================

    @Test
    @DisplayName("学生也能查班级列表（选课页面要用）")
    void student_canList() throws Exception {
        when(clazzService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 10L));

        mockMvc.perform(get("/clazz").header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("未登录查班级列表 → 401")
    void anonymous_cannotList() throws Exception {
        mockMvc.perform(get("/clazz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("查班级详情 → 200")
    void query_success() throws Exception {
        when(clazzService.query(CLAZZ_ID)).thenReturn(sampleClazz());

        mockMvc.perform(get("/clazz/" + CLAZZ_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("软件工程1班"));
    }

    @Test
    @DisplayName("查不存在的班级 → 404")
    void query_notFound() throws Exception {
        when(clazzService.query(9999L)).thenThrow(new BusinessException(404, "班级不存在"));

        mockMvc.perform(get("/clazz/9999").header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 写：仅教师/管理员 ====================

    @Test
    @DisplayName("学生新建班级 → 403，且不调用 Service")
    void student_cannotAdd() throws Exception {
        mockMvc.perform(post("/clazz")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"软件工程1班\",\"grade\":\"2023\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clazzService);
    }

    @Test
    @DisplayName("教师新建班级 → 200")
    void teacher_canAdd() throws Exception {
        mockMvc.perform(post("/clazz")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"软件工程1班\",\"grade\":\"2023\"}"))
                .andExpect(status().isOk());

        verify(clazzService).add(any());
    }

    @Test
    @DisplayName("新建班级：名字太短 → 400，且不调用 Service")
    void add_invalidName() throws Exception {
        mockMvc.perform(post("/clazz")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"grade\":\"2023\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clazzService);
    }

    @Test
    @DisplayName("新建班级：重名 → 409")
    void add_duplicateName() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "班级已注册"))
                .when(clazzService).add(any());

        mockMvc.perform(post("/clazz")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"软件工程1班\",\"grade\":\"2023\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("学生改班级 → 403")
    void student_cannotUpdate() throws Exception {
        mockMvc.perform(put("/clazz/" + CLAZZ_ID)
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"2024\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clazzService);
    }

    @Test
    @DisplayName("教师改班级 → 200，Service 收到路径 id")
    void teacher_canUpdate() throws Exception {
        when(clazzService.update(eq(CLAZZ_ID), any())).thenReturn(sampleClazz());

        mockMvc.perform(put("/clazz/" + CLAZZ_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grade\":\"2024\"}"))
                .andExpect(status().isOk());

        verify(clazzService).update(eq(CLAZZ_ID), any());
    }

    @Test
    @DisplayName("学生删班级 → 403")
    void student_cannotDelete() throws Exception {
        mockMvc.perform(delete("/clazz/" + CLAZZ_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clazzService);
    }

    @Test
    @DisplayName("删除仍有学生的班级 → 409（业务保护透传到 HTTP）")
    void delete_hasStudents() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "该班级下还有 3 名学生，请先调整学生所属班级"))
                .when(clazzService).delete(CLAZZ_ID);

        mockMvc.perform(delete("/clazz/" + CLAZZ_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("3 名学生")));
    }

    @Test
    @DisplayName("删除空班级 → 200")
    void delete_success() throws Exception {
        mockMvc.perform(delete("/clazz/" + CLAZZ_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk());

        verify(clazzService).delete(CLAZZ_ID);
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            ClazzController.class
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

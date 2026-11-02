package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.ScoreService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.ScoreResponse;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ScoreController 切片测试。
 * <p>
 * 这里最值得测的是权限的<b>方向不对称</b>：
 * <ul>
 *   <li>POST /scores、GET /scores（按课程查全班成绩）→ 只有教师/管理员</li>
 *   <li>GET /scores/my（查自己的成绩）→ <b>只有学生</b>，hasRole('STUDENT')</li>
 * </ul>
 * 最后这条反直觉：教师访问 /scores/my 会 403。因为该接口内部不接受任何 id 参数，
 * 完全依赖 token 里的身份，教师没有"自己的成绩"，放开只会得到空列表掩盖问题。
 */
@WebMvcTest(controllers = ScoreController.class)
@ContextConfiguration(classes = ScoreControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class ScoreControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long COURSE_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private ScoreService scoreService;

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

    private ScoreResponse sampleScore() {
        return ScoreResponse.builder()
                .id(1L).enrollmentId(7L)
                .studentName("张三").studentNo("S202500001")
                .courseName("数据结构")
                .value(new BigDecimal("88.5")).examType("期末")
                .build();
    }

    // ==================== POST /scores ====================

    @Test
    @DisplayName("教师录成绩 → 200")
    void teacher_canAdd() throws Exception {
        mockMvc.perform(post("/scores")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":7,\"value\":88.5,\"examType\":\"期末\"}"))
                .andExpect(status().isOk());

        verify(scoreService).add(any());
    }

    @Test
    @DisplayName("学生录成绩 → 403，且不调用 Service")
    void student_cannotAdd() throws Exception {
        mockMvc.perform(post("/scores")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":7,\"value\":100,\"examType\":\"期末\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scoreService);
    }

    @Test
    @DisplayName("分数 101 → 400（@DecimalMax 拦截）")
    void add_valueTooHigh() throws Exception {
        mockMvc.perform(post("/scores")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":7,\"value\":101,\"examType\":\"期末\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    @Test
    @DisplayName("分数 -1 → 400（@DecimalMin 拦截）")
    void add_valueNegative() throws Exception {
        mockMvc.perform(post("/scores")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":7,\"value\":-1,\"examType\":\"期末\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    @Test
    @DisplayName("同一选课同一考试类型重复录入 → 409")
    void add_duplicateExamType() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "该学生这门课的「期末」成绩已录入"))
                .when(scoreService).add(any());

        mockMvc.perform(post("/scores")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":7,\"value\":90,\"examType\":\"期末\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("期末")));
    }

    // ==================== GET /scores（按课程查全班） ====================

    @Test
    @DisplayName("教师按课程查成绩 → 200，返回列表含 courseName")
    void teacher_canPage() throws Exception {
        when(scoreService.page(any()))
                .thenReturn(new PageResult<>(1L, List.of(sampleScore()), 1L, 10L));

        mockMvc.perform(get("/scores?courseId=" + COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseName").value("数据结构"))
                .andExpect(jsonPath("$.data.records[0].studentNo").value("S202500001"));

        verify(scoreService).page(argThat(q ->
                q != null && Long.valueOf(COURSE_ID).equals(q.getCourseId())));
    }

    @Test
    @DisplayName("学生查全班成绩 → 403（只能看自己的）")
    void student_cannotPage() throws Exception {
        mockMvc.perform(get("/scores?courseId=" + COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scoreService);
    }

    @Test
    @DisplayName("不传 courseId → 400")
    void page_missingCourseId() throws Exception {
        mockMvc.perform(get("/scores")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    // ==================== GET /scores/my（只有学生） ====================

    @Test
    @DisplayName("学生查自己的成绩 → 200")
    void student_canQueryMy() throws Exception {
        when(scoreService.query()).thenReturn(List.of(sampleScore()));

        mockMvc.perform(get("/scores/my")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value("数据结构"))
                .andExpect(jsonPath("$.data[0].value").value(88.5));
    }

    @Test
    @DisplayName("教师访问 /scores/my → 403（hasRole STUDENT 的严格边界）")
    void teacher_cannotQueryMy() throws Exception {
        mockMvc.perform(get("/scores/my")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scoreService);
    }

    @Test
    @DisplayName("未登录访问 /scores/my → 401")
    void anonymous_cannotQueryMy() throws Exception {
        mockMvc.perform(get("/scores/my"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            ScoreController.class
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

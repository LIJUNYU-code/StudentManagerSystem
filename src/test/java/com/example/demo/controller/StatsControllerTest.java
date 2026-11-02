package com.example.demo.controller;

import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.StatsService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import com.example.demo.vo.StudentTrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StatsController 切片测试。
 * <p>
 * 权限方向与 /scores/my 相同的不对称设计：
 * <ul>
 *   <li>课程概览 / 分数段 / 班级对比 → 教师、管理员</li>
 *   <li>/stats/my/trend → <b>只有学生</b>，身份完全来自 token</li>
 * </ul>
 * 这里还额外锁住一条契约：<b>空课程返回全 0 而不是 null</b>。
 * 前端 ECharts 拿到 null 会画不出图（不是显示空白，而是抛异常），
 * 所以这条不能只靠 Service 单测，要在 HTTP 层验证 JSON 里真的是 0。
 */
@WebMvcTest(controllers = StatsController.class)
@ContextConfiguration(classes = StatsControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class StatsControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long COURSE_ID = 5L;
    private static final long CLAZZ_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private StatsService statsService;

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

    private CourseSummaryVO summary() {
        return CourseSummaryVO.builder()
                .count(43L)
                .avg(new BigDecimal("71.5"))
                .max(new BigDecimal("100.0"))
                .min(new BigDecimal("41.0"))
                .passRate(new BigDecimal("0.6744"))
                .build();
    }

    // ==================== GET /stats/course/{id}/summary ====================

    @Test
    @DisplayName("教师查课程概览 → 200 且字段齐全")
    void teacher_canQuerySummary() throws Exception {
        when(statsService.courseSummary(COURSE_ID)).thenReturn(summary());

        mockMvc.perform(get("/stats/course/{id}/summary", COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(43))
                .andExpect(jsonPath("$.data.avg").value(71.5))
                .andExpect(jsonPath("$.data.max").value(100.0))
                .andExpect(jsonPath("$.data.min").value(41.0))
                .andExpect(jsonPath("$.data.passRate").value(0.6744));
    }

    @Test
    @DisplayName("管理员也能查课程概览 → 200")
    void admin_canQuerySummary() throws Exception {
        when(statsService.courseSummary(COURSE_ID)).thenReturn(summary());

        mockMvc.perform(get("/stats/course/{id}/summary", COURSE_ID)
                        .header("Authorization", token(9L, "admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生查课程概览 → 403，且不调用 Service（看别人成绩的视角）")
    void student_cannotQuerySummary() throws Exception {
        mockMvc.perform(get("/stats/course/{id}/summary", COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statsService);
    }

    @Test
    @DisplayName("未登录 → 401")
    void anonymous_cannotQuerySummary() throws Exception {
        mockMvc.perform(get("/stats/course/{id}/summary", COURSE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("空课程：JSON 里是 0 而不是 null（前端图表依赖这条契约）")
    void emptyCourse_returnsZeroNotNull() throws Exception {
        when(statsService.courseSummary(COURSE_ID)).thenReturn(CourseSummaryVO.builder()
                .count(0L)
                .avg(BigDecimal.ZERO)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ZERO)
                .passRate(BigDecimal.ZERO)
                .build());

        mockMvc.perform(get("/stats/course/{id}/summary", COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.avg").value(0))
                // null 会让 jsonPath 断言失败，所以这行等于在断言"不是 null"
                .andExpect(jsonPath("$.data.avg").isNotEmpty());
    }

    // ==================== GET /stats/course/{id}/distribution ====================

    @Test
    @DisplayName("分数段：返回 5 段且顺序固定")
    void teacher_canQueryDistribution() throws Exception {
        when(statsService.courseDistribution(COURSE_ID)).thenReturn(List.of(
                new ScoreBucketVO("0-59", 14L),
                new ScoreBucketVO("60-69", 6L),
                new ScoreBucketVO("70-79", 6L),
                new ScoreBucketVO("80-89", 7L),
                new ScoreBucketVO("90-100", 10L)));

        mockMvc.perform(get("/stats/course/{id}/distribution", COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].bucket").value("0-59"))
                .andExpect(jsonPath("$.data[0].count").value(14))
                .andExpect(jsonPath("$.data[4].bucket").value("90-100"));
    }

    @Test
    @DisplayName("学生查分数段 → 403")
    void student_cannotQueryDistribution() throws Exception {
        mockMvc.perform(get("/stats/course/{id}/distribution", COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statsService);
    }

    // ==================== GET /stats/clazz/{id}/compare ====================

    @Test
    @DisplayName("教师查班级对比 → 200")
    void teacher_canQueryCompare() throws Exception {
        when(statsService.clazzCompare(CLAZZ_ID)).thenReturn(List.of(
                CourseAvgVO.builder().courseId(1L).courseName("数据结构")
                        .avg(new BigDecimal("70.1")).count(20L).build()));

        mockMvc.perform(get("/stats/clazz/{id}/compare", CLAZZ_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value("数据结构"))
                .andExpect(jsonPath("$.data[0].avg").value(70.1));
    }

    @Test
    @DisplayName("学生查班级对比 → 403")
    void student_cannotQueryCompare() throws Exception {
        mockMvc.perform(get("/stats/clazz/{id}/compare", CLAZZ_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statsService);
    }

    // ==================== GET /stats/my/trend（只有学生） ====================

    @Test
    @DisplayName("学生查自己的成绩趋势 → 200")
    void student_canQueryMyTrend() throws Exception {
        when(statsService.myTrend()).thenReturn(List.of(
                new StudentTrendVO("数据结构", "final", new BigDecimal("44.0"))));

        mockMvc.perform(get("/stats/my/trend")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value("数据结构"))
                .andExpect(jsonPath("$.data[0].examType").value("final"))
                .andExpect(jsonPath("$.data[0].value").value(44.0));
    }

    @Test
    @DisplayName("教师访问 /stats/my/trend → 403（教师没有「自己的成绩」）")
    void teacher_cannotQueryMyTrend() throws Exception {
        mockMvc.perform(get("/stats/my/trend")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(statsService);
    }

    @Test
    @DisplayName("未登录访问 /stats/my/trend → 401")
    void anonymous_cannotQueryMyTrend() throws Exception {
        mockMvc.perform(get("/stats/my/trend"))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            StatsController.class
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

package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.EnrollmentService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.EnrollmentResponse;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EnrollmentController 切片测试。
 * <p>
 * 选课三个端点全部只对教师/管理员开放。学生自己不能选课——
 * 当前设计里选课是教务行为，不是学生自助行为（二期做学生自助端时才需要放开）。
 * 测试固定住这个边界，防止以后误放开。
 */
@WebMvcTest(controllers = EnrollmentController.class)
@ContextConfiguration(classes = EnrollmentControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class EnrollmentControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long ADMIN_ID = 100L;
    private static final long ENROLLMENT_ID = 7L;
    private static final long COURSE_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private EnrollmentService enrollmentService;

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

    // ==================== POST /enrollments ====================

    @Test
    @DisplayName("教师选课 → 200")
    void teacher_canAdd() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"courseId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(enrollmentService).add(any());
    }

    @Test
    @DisplayName("管理员也能选课 → 200（hasAnyRole 包含 ADMIN）")
    void admin_canAdd() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("Authorization", token(ADMIN_ID, "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"courseId\":5}"))
                .andExpect(status().isOk());

        verify(enrollmentService).add(any());
    }

    @Test
    @DisplayName("学生选课 → 403，且不调用 Service")
    void student_cannotAdd() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"courseId\":5}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(enrollmentService);
    }

    @Test
    @DisplayName("未登录选课 → 401")
    void anonymous_cannotAdd() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"courseId\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("缺少 courseId → 400，且不调用 Service")
    void add_missingCourseId() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(enrollmentService);
    }

    @Test
    @DisplayName("studentId 传 0 → 400（@Positive 拦截）")
    void add_nonPositiveStudentId() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":0,\"courseId\":5}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(enrollmentService);
    }

    @Test
    @DisplayName("重复选课 → 409")
    void add_duplicate() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "该学生已选过这门课"))
                .when(enrollmentService).add(any());

        mockMvc.perform(post("/enrollments")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"courseId\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ==================== GET /enrollments ====================

    @Test
    @DisplayName("按课程查选课名单 → 200，courseId 正确绑定")
    void list_byCourse() throws Exception {
        when(enrollmentService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 10L));

        mockMvc.perform(get("/enrollments?courseId=" + COURSE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk());

        verify(enrollmentService).page(argThat(q ->
                q != null && Long.valueOf(COURSE_ID).equals(q.getCourseId())));
    }

    @Test
    @DisplayName("不传 courseId 查名单 → 400（否则会全表扫）")
    void list_missingCourseId() throws Exception {
        mockMvc.perform(get("/enrollments")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(enrollmentService);
    }

    @Test
    @DisplayName("学生查选课名单 → 403")
    void student_cannotList() throws Exception {
        mockMvc.perform(get("/enrollments?courseId=" + COURSE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(enrollmentService);
    }

    // ==================== DELETE /enrollments/{id} ====================

    @Test
    @DisplayName("教师退课 → 200")
    void teacher_canDelete() throws Exception {
        mockMvc.perform(delete("/enrollments/" + ENROLLMENT_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk());

        verify(enrollmentService).delete(ENROLLMENT_ID);
    }

    @Test
    @DisplayName("学生退课 → 403")
    void student_cannotDelete() throws Exception {
        mockMvc.perform(delete("/enrollments/" + ENROLLMENT_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(enrollmentService);
    }

    @Test
    @DisplayName("已有成绩时退课 → 409（业务保护透传到 HTTP）")
    void delete_hasScore() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "该选课已有成绩记录，请先删除成绩"))
                .when(enrollmentService).delete(ENROLLMENT_ID);

        mockMvc.perform(delete("/enrollments/" + ENROLLMENT_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("成绩")));
    }

    @Test
    @DisplayName("退不存在的选课 → 404")
    void delete_notFound() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(404, "选课记录不存在"))
                .when(enrollmentService).delete(9999L);

        mockMvc.perform(delete("/enrollments/9999")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isNotFound());
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            EnrollmentController.class
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

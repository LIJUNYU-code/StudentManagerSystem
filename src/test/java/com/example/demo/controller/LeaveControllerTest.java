package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.LeaveService;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.LeaveResponse;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LeaveController 切片测试。
 * <p>
 * 请假模块的权限边界比之前的模块更细，因为它有<b>两种角色各管一半</b>：
 * <ul>
 *   <li>学生：POST /leave、GET /leave/my、PUT /leave/{id}/cancel</li>
 *   <li>教师/管理员：GET /leave、PUT /leave/{id}/approve、PUT /leave/{id}/reject</li>
 * </ul>
 * 特别值得注意的是 approve 和 reject 是<b>两个独立接口</b>，不是"一个接口传 status"。
 * 这样意图写在 URL 里，权限也就能分别控制——未来若要"只有班主任能批通过、
 * 管理员能批驳回"这种规则，改注解即可，不用改请求体格式。
 */
@WebMvcTest(controllers = LeaveController.class)
@ContextConfiguration(classes = LeaveControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class LeaveControllerTest {

    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long LEAVE_ID = 500L;
    private static final long COURSE_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private LeaveService leaveService;

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

    private LeaveResponse sampleLeave(int status) {
        return LeaveResponse.builder()
                .id(LEAVE_ID).studentId(STUDENT_ID).studentNo("S202500101").studentName("张三")
                .courseId(COURSE_ID).courseName("数据结构")
                .startDate(LocalDate.of(2026, 11, 15)).endDate(LocalDate.of(2026, 11, 17))
                .days(3L).reason("家中有事").status(status)
                .build();
    }

    private PageResult<LeaveResponse> pageOf(List<LeaveResponse> list) {
        return new PageResult<>(list.size(), list, 1, 10);
    }

    // ==================== POST /leave ====================

    @Test
    @DisplayName("学生提交请假 → 200")
    void student_canCreate() throws Exception {
        when(leaveService.create(any())).thenReturn(sampleLeave(0));

        mockMvc.perform(post("/leave")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":10,"startDate":"2026-11-15","endDate":"2026-11-17","reason":"家中有事"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.days").value(3));
    }

    @Test
    @DisplayName("教师提交请假 → 403（教师没有「自己的请假」）")
    void teacher_cannotCreate() throws Exception {
        mockMvc.perform(post("/leave")
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":10,"startDate":"2026-11-15","endDate":"2026-11-17","reason":"x"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("理由为空 → 400（@NotBlank）")
    void create_emptyReason() throws Exception {
        mockMvc.perform(post("/leave")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":10,"startDate":"2026-11-15","endDate":"2026-11-17","reason":""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("缺 courseId → 400")
    void create_missingCourseId() throws Exception {
        mockMvc.perform(post("/leave")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-11-15","endDate":"2026-11-17","reason":"家中有事"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("日期格式错误 → 400")
    void create_badDateFormat() throws Exception {
        mockMvc.perform(post("/leave")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":10,"startDate":"11/15","endDate":"2026-11-17","reason":"x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("理由超过 300 字 → 400")
    void create_reasonTooLong() throws Exception {
        String longReason = "a".repeat(301);
        mockMvc.perform(post("/leave")
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":10,\"startDate\":\"2026-11-15\",\"endDate\":\"2026-11-17\",\"reason\":\""
                                + longReason + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("未登录提交 → 401")
    void anonymous_cannotCreate() throws Exception {
        mockMvc.perform(post("/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":10,"startDate":"2026-11-15","endDate":"2026-11-17","reason":"x"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /leave/my ====================

    @Test
    @DisplayName("学生查自己的请假 → 200")
    void student_canQueryMy() throws Exception {
        when(leaveService.myPage(any())).thenReturn(pageOf(List.of(sampleLeave(1))));

        mockMvc.perform(get("/leave/my")
                        .param("page", "1").param("size", "10")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].courseName").value("数据结构"));
    }

    @Test
    @DisplayName("教师访问 /leave/my → 403")
    void teacher_cannotQueryMy() throws Exception {
        mockMvc.perform(get("/leave/my")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveService);
    }

    // ==================== GET /leave ====================

    @Test
    @DisplayName("教师查全部请假 → 200")
    void teacher_canQueryAll() throws Exception {
        when(leaveService.page(any())).thenReturn(pageOf(List.of(sampleLeave(0))));

        mockMvc.perform(get("/leave")
                        .param("status", "0")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value(0));
    }

    @Test
    @DisplayName("管理员也能查全部 → 200")
    void admin_canQueryAll() throws Exception {
        when(leaveService.page(any())).thenReturn(pageOf(List.of()));

        mockMvc.perform(get("/leave")
                        .header("Authorization", token(9L, "admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生查全部请假 → 403（越权看别人请假）")
    void student_cannotQueryAll() throws Exception {
        mockMvc.perform(get("/leave")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("状态参数越界（4）→ 400")
    void query_statusOutOfRange() throws Exception {
        mockMvc.perform(get("/leave")
                        .param("status", "4")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("page=0 → 400")
    void query_pageZero() throws Exception {
        mockMvc.perform(get("/leave")
                        .param("page", "0")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isBadRequest());
    }

    // ==================== PUT /leave/{id}/approve ====================

    @Test
    @DisplayName("教师审批通过 → 200，返回状态 1")
    void teacher_canApprove() throws Exception {
        when(leaveService.approve(anyLong(), any())).thenReturn(sampleLeave(1));

        mockMvc.perform(put("/leave/{id}/approve", LEAVE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"情况属实\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    @DisplayName("审批通过可以不传 body（remark 选填）")
    void approve_withoutBody() throws Exception {
        when(leaveService.approve(anyLong(), any())).thenReturn(sampleLeave(1));

        mockMvc.perform(put("/leave/{id}/approve", LEAVE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生审批 → 403")
    void student_cannotApprove() throws Exception {
        mockMvc.perform(put("/leave/{id}/approve", LEAVE_ID)
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("未登录审批 → 401")
    void anonymous_cannotApprove() throws Exception {
        mockMvc.perform(put("/leave/{id}/approve", LEAVE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PUT /leave/{id}/reject ====================

    @Test
    @DisplayName("教师驳回 → 200，返回状态 2")
    void teacher_canReject() throws Exception {
        when(leaveService.reject(anyLong(), any())).thenReturn(sampleLeave(2));

        mockMvc.perform(put("/leave/{id}/reject", LEAVE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"理由不充分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
    }

    @Test
    @DisplayName("学生驳回 → 403")
    void student_cannotReject() throws Exception {
        mockMvc.perform(put("/leave/{id}/reject", LEAVE_ID)
                        .header("Authorization", token(STUDENT_ID, "student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveService);
    }

    // ==================== PUT /leave/{id}/cancel ====================

    @Test
    @DisplayName("学生撤销自己的申请 → 200")
    void student_canCancel() throws Exception {
        mockMvc.perform(put("/leave/{id}/cancel", LEAVE_ID)
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("教师撤销 → 403（只有学生能撤自己的）")
    void teacher_cannotCancel() throws Exception {
        mockMvc.perform(put("/leave/{id}/cancel", LEAVE_ID)
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(leaveService);
    }

    @Test
    @DisplayName("未登录撤销 → 401")
    void anonymous_cannotCancel() throws Exception {
        mockMvc.perform(put("/leave/{id}/cancel", LEAVE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            LeaveController.class
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

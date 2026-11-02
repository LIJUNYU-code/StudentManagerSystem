package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.service.UserService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.AdminUserResponse;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminUserController 切片测试。
 * <p>
 * 类级 @PreAuthorize("hasRole('ADMIN')") 是本项目唯一的"管理员专属"区域。
 * 重点验证：教师访问全部 403 —— 教师是注册后待审核的正常用户，
 * 但绝不能因此获得审核别人、禁用别人的权力（横向提权）。
 * 另外验证响应体不含 password（AdminUserResponse 刻意不映射该字段）。
 */
@WebMvcTest(controllers = AdminUserController.class)
@ContextConfiguration(classes = AdminUserControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class AdminUserControllerTest {

    private static final long ADMIN_ID = 100L;
    private static final long TEACHER_ID = 1L;
    private static final long STUDENT_ID = 42L;
    private static final long TARGET_USER_ID = 9L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

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

    private AdminUserResponse sampleUser() {
        return AdminUserResponse.builder()
                .id(TARGET_USER_ID).username("teacher01").realName("李老师")
                .role("teacher").status(0).build();
    }

    // ==================== GET /admin/user ====================

    @Test
    @DisplayName("管理员查用户列表 → 200，响应不含 password")
    void admin_canPage() throws Exception {
        when(userService.page(any()))
                .thenReturn(new PageResult<>(1L, List.of(sampleUser()), 1L, 10L));

        mockMvc.perform(get("/admin/user")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("teacher01"))
                .andExpect(jsonPath("$.data.records[0].status").value(0))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist());
    }

    @Test
    @DisplayName("教师查用户列表 → 403（横向提权防线）")
    void teacher_cannotPage() throws Exception {
        mockMvc.perform(get("/admin/user")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("学生查用户列表 → 403")
    void student_cannotPage() throws Exception {
        mockMvc.perform(get("/admin/user")
                        .header("Authorization", token(STUDENT_ID, "student")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("未登录查用户列表 → 401")
    void anonymous_cannotPage() throws Exception {
        mockMvc.perform(get("/admin/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("pageSize 超限 → 400")
    void page_pageSizeTooLarge() throws Exception {
        mockMvc.perform(get("/admin/user?pageSize=500")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("不传任何分页参数 → page/pageSize 用默认值 1/10（不能是 null）")
    void page_noParams_usesDefaults() throws Exception {
        when(userService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 10L));

        mockMvc.perform(get("/admin/user")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk());

        // 回归防线：2026-11-02 实测 GET /admin/user 曾返回 500，
        // 原因是 UserReviewQueryDTO 的 page/pageSize 没有默认值，
        // 缺省时是 null，Service 里 new Page<>(null, null) 直接 NPE。
        // 这里断言的是"绑定后的 DTO 已经带上默认值"，比断言状态码更能锁死根因。
        verify(userService).page(argThat(q ->
                q != null
                        && Integer.valueOf(1).equals(q.getPage())
                        && Integer.valueOf(10).equals(q.getPageSize())));
    }

    @Test
    @DisplayName("只传 page → pageSize 仍回落到默认 10")
    void page_onlyPage_defaultsPageSize() throws Exception {
        when(userService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 2L, 10L));

        mockMvc.perform(get("/admin/user?page=2")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk());

        verify(userService).page(argThat(q ->
                q != null
                        && Integer.valueOf(2).equals(q.getPage())
                        && Integer.valueOf(10).equals(q.getPageSize())));
    }

    @Test
    @DisplayName("只传 pageSize → page 仍回落到默认 1")
    void page_onlyPageSize_defaultsPage() throws Exception {
        when(userService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 5L));

        mockMvc.perform(get("/admin/user?pageSize=5")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk());

        verify(userService).page(argThat(q ->
                q != null
                        && Integer.valueOf(1).equals(q.getPage())
                        && Integer.valueOf(5).equals(q.getPageSize())));
    }

    @Test
    @DisplayName("按 status=0 过滤待审核用户 → 条件正确传递")
    void page_filterByStatus() throws Exception {
        when(userService.page(any())).thenReturn(new PageResult<>(0L, List.of(), 1L, 10L));

        mockMvc.perform(get("/admin/user?status=0")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk());

        verify(userService).page(argThat(q ->
                q != null && Integer.valueOf(0).equals(q.getStatus())));
    }

    // ==================== PUT /admin/user/{id}/approve ====================

    @Test
    @DisplayName("管理员审核通过 → 200")
    void admin_canApprove() throws Exception {
        mockMvc.perform(put("/admin/user/" + TARGET_USER_ID + "/approve")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk());

        verify(userService).approve(TARGET_USER_ID);
    }

    @Test
    @DisplayName("教师审核别人 → 403，且不调用 Service")
    void teacher_cannotApprove() throws Exception {
        mockMvc.perform(put("/admin/user/" + TARGET_USER_ID + "/approve")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("审核不存在的用户 → 404")
    void approve_notFound() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(404, "用户不存在"))
                .when(userService).approve(9999L);

        mockMvc.perform(put("/admin/user/9999/approve")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("审核已通过的用户 → 409")
    void approve_alreadyApproved() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "仅待审核用户需要审核"))
                .when(userService).approve(TARGET_USER_ID);

        mockMvc.perform(put("/admin/user/" + TARGET_USER_ID + "/approve")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isConflict());
    }

    // ==================== PUT /admin/user/{id}/disable ====================

    @Test
    @DisplayName("管理员禁用用户 → 200")
    void admin_canDisable() throws Exception {
        mockMvc.perform(put("/admin/user/" + TARGET_USER_ID + "/disable")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isOk());

        verify(userService).disable(TARGET_USER_ID);
    }

    @Test
    @DisplayName("教师禁用别人 → 403")
    void teacher_cannotDisable() throws Exception {
        mockMvc.perform(put("/admin/user/" + TARGET_USER_ID + "/disable")
                        .header("Authorization", token(TEACHER_ID, "teacher")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("管理员禁用自己 → 409（Service 层自操作保护透传）")
    void disable_self() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "请勿自操作"))
                .when(userService).disable(ADMIN_ID);

        mockMvc.perform(put("/admin/user/" + ADMIN_ID + "/disable")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("请勿自操作")));
    }

    @Test
    @DisplayName("id 传非数字 → 400（类型转换失败不是 500）")
    void disable_badIdType() throws Exception {
        mockMvc.perform(put("/admin/user/abc/disable")
                        .header("Authorization", token(ADMIN_ID, "admin")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            AdminUserController.class
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

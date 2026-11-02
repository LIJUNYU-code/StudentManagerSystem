package com.example.demo.controller;

import com.example.demo.config.JwtAuthenticationFilter;
import com.example.demo.config.SecurityConfig;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.service.UserService;
import com.example.demo.util.JwtUtil;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthUserController 切片测试。
 * <p>
 * 这个类最值得测的是<b>匿名可访问边界</b>：注册和登录必须免登录，
 * 而登出和改密必须登录。这条边界一旦写错（比如 permitAll 多写一个路径），
 * 就是把修改密码接口暴露给匿名用户，属于安全事故而不是普通 bug。
 */
@WebMvcTest(controllers = AuthUserController.class)
@ContextConfiguration(classes = AuthUserControllerTest.SliceConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=Y2hhbmdlLW1lLWRldi1vbmx5LXNlY3JldC1iYXNlNjQtc3RyaW5nLWZvci1sb2NhbC1ydW4=",
        "jwt.expiration=7200000"
})
class AuthUserControllerTest {

    private static final long USER_ID = 7L;

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

    // ==================== 匿名可访问 ====================

    @Test
    @DisplayName("注册：无需 token 即可访问（新教师注册的前提）")
    void register_isAnonymous() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"teacher01","password":"Abc12345","realName":"王老师"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userService).register(any());
    }

    @Test
    @DisplayName("注册：用户名太短 → 400，且不调用 Service")
    void register_invalidUsername() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"Abc12345","realName":"王老师"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("注册：用户名重复 → 409")
    void register_duplicateUsername() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "用户名已被占用"))
                .when(userService).register(any());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"teacher01","password":"Abc12345","realName":"王老师"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("登录：无需 token，成功返回 token")
    void login_isAnonymous() throws Exception {
        when(userService.login(any())).thenReturn(
                new UserLoginResponseDTO(USER_ID, "teacher01", "fake-token"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"teacher01","password":"Abc12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("fake-token"));
    }

    @Test
    @DisplayName("登录：待审核账号 → 403（能登录但进不去，提示原因）")
    void login_pendingReview() throws Exception {
        when(userService.login(any())).thenThrow(new BusinessException(403, "账号待审核"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"teacher01","password":"Abc12345"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== 必须登录 ====================

    @Test
    @DisplayName("登出：不带 token → 401（登出也必须先登录）")
    void logout_requiresAuth() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登出：带 token → 200，且把 token 交给黑名单")
    void logout_revokesToken() throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", token(USER_ID, "teacher")))
                .andExpect(status().isOk());

        verify(tokenBlacklistService).revoke(any());
    }

    @Test
    @DisplayName("改密：不带 token → 401（不能匿名改别人密码）")
    void changePassword_requiresAuth() throws Exception {
        mockMvc.perform(post("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"Abc12345","newPassword":"NewPass123"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("改密：身份取自 token，而不是请求体（防越权改他人密码）")
    void changePassword_usesTokenIdentity() throws Exception {
        when(userService.changePassword(eq(USER_ID), any())).thenReturn(
                new UserLoginResponseDTO(USER_ID, "teacher01", "new-token"));

        mockMvc.perform(post("/auth/password")
                        .header("Authorization", token(USER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"Abc12345","newPassword":"NewPass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("new-token"));

        // 关键断言：Service 收到的 userId 来自 token（7），而不是请求体里任何字段
        verify(userService).changePassword(eq(USER_ID), any());
    }

    @Test
    @DisplayName("改密：新密码太短 → 400，且不调用 Service")
    void changePassword_invalidNewPassword() throws Exception {
        mockMvc.perform(post("/auth/password")
                        .header("Authorization", token(USER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"Abc12345","newPassword":"123"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(anyLong(), any());
    }

    @Test
    @DisplayName("改密：旧密码错误 → 400")
    void changePassword_wrongOldPassword() throws Exception {
        when(userService.changePassword(anyLong(), any()))
                .thenThrow(new BusinessException(400, "当前密码不正确"));

        mockMvc.perform(post("/auth/password")
                        .header("Authorization", token(USER_ID, "teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"WrongPass1","newPassword":"NewPass123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Configuration
    @EnableWebSecurity
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            AuthUserController.class
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

package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.ChangePasswordRequestDTO;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "账号认证", description = "教师/管理员账号的注册、登录、登出与修改密码")
@RestController

@AllArgsConstructor
@RequestMapping("/auth")
public class AuthUserController {

    private final  UserService userService;
    private final TokenBlacklistService tokenBlacklistService;

    @Operation(summary = "教师/管理员注册", description = "公开接口，无需登录。注册后账号处于待审核状态，需管理员在「用户审核」里通过后才能登录")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterRequestDTO userRegisterRequestDTO) {
        userService.register(userRegisterRequestDTO);
        return Result.success();
    }

    @Operation(summary = "教师/管理员登录", description = "公开接口，无需登录。成功返回 token；账号待审核或被禁用时登录失败")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginRequestDTO userLoginRequestDTO) {
        UserLoginResponseDTO login = userService.login(userLoginRequestDTO);
        return Result.success(login);
    }

    /**
     * 登出（阶段7）：把当前请求的 token 拉入 Redis 黑名单，
     * TTL = token 剩余有效期。前端收到成功后应删除本地存储的 token。
     */
    @Operation(summary = "登出", description = "把当前 token 拉入 Redis 黑名单，使其在剩余有效期内也立即失效。前端收到成功后应删除本地 token")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenBlacklistService.revoke(authorization.substring(7));
        }
        return Result.success();
    }

    /**
     * 修改密码（阶段8）：成功后旧密码作废、该用户所有旧 token 失效（iat 划线），
     * 返回重新签发的 token 使当前会话无缝续期。认证保护内，用户身份取自 token。
     */
    @Operation(summary = "修改密码", description = "需登录。校验旧密码；成功后该用户所有旧 token 立即失效，并返回新 token 让当前会话无缝续期")
    @PostMapping("/password")
    public Result<UserLoginResponseDTO> changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto) {
        // principal 就是过滤器塞进来的用户 id（Long），不信任任何请求体里的身份字段
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = (Long) auth.getPrincipal();
        return Result.success(userService.changePassword(currentUserId, dto));
    }


}

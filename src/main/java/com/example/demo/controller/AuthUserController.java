package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.service.TokenBlacklistService;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController

@AllArgsConstructor
@RequestMapping("/auth")
public class AuthUserController {

    private final  UserService userService;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterRequestDTO userRegisterRequestDTO) {
        userService.register(userRegisterRequestDTO);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginRequestDTO userLoginRequestDTO) {
        UserLoginResponseDTO login = userService.login(userLoginRequestDTO);
        return Result.success(login);
    }

    /**
     * 登出（阶段7）：把当前请求的 token 拉入 Redis 黑名单，
     * TTL = token 剩余有效期。前端收到成功后应删除本地存储的 token。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenBlacklistService.revoke(authorization.substring(7));
        }
        return Result.success();
    }


}

package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
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


}

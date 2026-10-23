package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.dto.UserReviewQueryDTO;
import com.example.demo.vo.AdminUserResponse;
import jakarta.validation.Valid;

public interface UserService {

    void  register(UserRegisterRequestDTO userRegisterRequestDTO);

    UserLoginResponseDTO login(UserLoginRequestDTO userLoginRequestDTO);

    PageResult<AdminUserResponse> page(@Valid UserReviewQueryDTO userReviewQueryDTO);

    void approve(Long id);

    void disable(Long id);
}

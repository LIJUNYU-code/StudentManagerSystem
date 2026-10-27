package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.ChangePasswordRequestDTO;
import com.example.demo.dto.UserLoginRequestDTO;
import com.example.demo.dto.UserLoginResponseDTO;
import com.example.demo.dto.UserRegisterRequestDTO;
import com.example.demo.dto.UserReviewQueryDTO;
import com.example.demo.vo.AdminUserResponse;
import jakarta.validation.Valid;

public interface UserService {

    void  register(UserRegisterRequestDTO userRegisterRequestDTO);

    UserLoginResponseDTO login(UserLoginRequestDTO userLoginRequestDTO);

    /**
     * 修改密码（阶段8）：校验旧密码 → 更新密码 + pwd_change_time 划线
     * → 返回重新签发的 token（iat 晚于划线时间，当前会话不掉线）。
     */
    UserLoginResponseDTO changePassword(Long currentUserId, ChangePasswordRequestDTO dto);

    PageResult<AdminUserResponse> page(@Valid UserReviewQueryDTO userReviewQueryDTO);

    void approve(Long id);

    void disable(Long id);
}

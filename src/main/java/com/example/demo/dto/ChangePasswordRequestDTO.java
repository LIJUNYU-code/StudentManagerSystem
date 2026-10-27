package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求体（阶段8：改密强制下线）。
 * 成功后：旧密码作废 + 该用户所有已签发 token 全部失效（iat 划线机制）。
 */
@Data
public class ChangePasswordRequestDTO {

    /** 当前密码（防止拿到已登录 token 的人盗改密码） */
    @NotBlank
    private String oldPassword;

    /** 新密码，规则与注册一致：6~32 位 */
    @NotBlank
    @Size(min = 6, max = 32)
    private String newPassword;
}

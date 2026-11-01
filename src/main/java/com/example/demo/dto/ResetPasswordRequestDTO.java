package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 教师/管理员重置学生密码请求体。
 * 与注册的密码规则保持一致（6~18 位、无空白字符），
 * 避免"重置出来的密码比注册时宽松"这种规则不一致。
 */
@Data
public class ResetPasswordRequestDTO {

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^\\S{6,18}$", message = "密码长度必须为6到18位，且不能包含空白字符")
    private String newPassword;
}

package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录请求参数：学号 + 密码 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^S\\d{9}$", message = "学号格式为 S 开头 + 9 位数字")
    private String studentNo;

    @NotBlank(message = "密码不能为空")
    private String password;
}

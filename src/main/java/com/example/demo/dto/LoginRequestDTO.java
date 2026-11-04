package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录请求参数：学号 + 密码 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "学生登录请求")
public class LoginRequestDTO {

    @Schema(description = "学号，S 开头 + 9 位数字", example = "S202300001")
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^S\\d{9}$", message = "学号格式为 S 开头 + 9 位数字")
    private String studentNo;

    @Schema(description = "登录密码", example = "abc12345")
    @NotBlank(message = "密码不能为空")
    private String password;
}

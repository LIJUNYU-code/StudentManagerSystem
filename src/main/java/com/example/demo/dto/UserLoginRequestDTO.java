package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "教师/管理员登录请求")
@Data
public class UserLoginRequestDTO {

        @Schema(description = "用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "用户名不能为空")
        private String username;

        @Schema(description = "密码", example = "Demo@2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "密码不能为空")
        private String password;



}

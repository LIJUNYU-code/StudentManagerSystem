package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 教师/管理员重置学生密码请求体。
 * 与注册的密码规则保持一致（6~18 位、无空白字符），
 * 避免"重置出来的密码比注册时宽松"这种规则不一致。
 */
@Schema(description = "重置学生密码请求。教师/管理员使用，不需要旧密码")
@Data
public class ResetPasswordRequestDTO {

    @Schema(description = "新密码，6~18 位且不能含空白字符", example = "newPass123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^\\S{6,18}$", message = "密码长度必须为6到18位，且不能包含空白字符")
    private String newPassword;
}

package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求体（阶段8：改密强制下线）。
 * 成功后：旧密码作废 + 该用户所有已签发 token 全部失效（iat 划线机制）。
 */
@Schema(description = "修改密码请求。成功后该用户所有旧 token 立即失效，并返回新 token")
@Data
public class ChangePasswordRequestDTO {

    /** 当前密码（防止拿到已登录 token 的人盗改密码） */
    @Schema(description = "当前密码，必须与库中一致", example = "oldPass123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    /** 新密码，规则与注册一致：6~32 位 */
    @Schema(description = "新密码，6~32 位，不能与当前密码相同", example = "newPass456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度必须为 6~32 位")
    private String newPassword;
}

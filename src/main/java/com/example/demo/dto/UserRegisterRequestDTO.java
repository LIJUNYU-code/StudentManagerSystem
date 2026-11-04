package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "教师/管理员注册请求。注册后账号为「待审核」状态，需管理员审核通过才能登录")
@Data
public class UserRegisterRequestDTO {

    @Schema(description = "登录用户名，4~20 位字母/数字/下划线，全局唯一", example = "teacher_zhang",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^\\w{4,20}$", message = "用户名必须为 4~20 位字母、数字或下划线")
    private String username;

    @Schema(description = "登录密码，6~32 位。服务端用 BCrypt 加密存储", example = "abc12345",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须为 6~32 位")
    private String password;

    @Schema(description = "真实姓名，用于列表和审批环节展示", example = "张老师",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "真实姓名不能为空")
    private String realName;



}

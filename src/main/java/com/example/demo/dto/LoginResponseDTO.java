package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功响应：不含任何敏感字段 */
@Schema(description = "学生登录成功的返回结果（不含密码等敏感字段）")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginResponseDTO {

    @Schema(description = "学生 id", example = "3")
    private Long id;

    @Schema(description = "学号", example = "S202300001")
    private String studentNo;

    @Schema(description = "学生姓名", example = "张三")
    private String name;

    @Schema(description = "JWT token。后续请求放到请求头 Authorization: Bearer <token>",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;
}

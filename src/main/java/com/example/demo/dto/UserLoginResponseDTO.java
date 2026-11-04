package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Schema(description = "登录成功的返回结果")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserLoginResponseDTO {


        @Schema(description = "用户 id", example = "1")
        private Long id;

        @Schema(description = "用户名", example = "admin")
        private String username;

        @Schema(description = "JWT token。后续请求放到请求头 Authorization: Bearer <token>",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        private String token;


}

package com.example.demo.vo;

import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;

    private String userName;

    private  String realName;

    private String role;

    private Integer status;

    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .userName(user.getUsername())
                .realName(user.getRealName())
                .role("教师")
                .status(0)
                .build();
        return userResponse;
    }
}

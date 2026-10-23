package com.example.demo.vo;

import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserResponse {
    
        private Long id;
        
        private String username;
        
        private String realName;
        
        private String role;
        
        private Integer status;
        
        private LocalDateTime createTime;
        
        private LocalDateTime updateTime;
        
        
        public static AdminUserResponse from(User user) {
            return AdminUserResponse.builder().id(
                    user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .role(user.getRole())
                    .status(user.getStatus())
                    .createTime(user.getCreateTime())
                    .updateTime(user.getUpdateTime())
                    .build();
        }


}

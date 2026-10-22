package com.example.demo.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginRequestDTO {

        @NotBlank
        private String username;
        @NotBlank
        private String password;



}

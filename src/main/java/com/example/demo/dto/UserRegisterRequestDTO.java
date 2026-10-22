package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterRequestDTO {

    @NotBlank
    @Pattern(regexp = "^\\w{4,20}$")
    private String username;
    @NotNull
    @Size(min=6,max=32)
    private String password;
    @NotBlank
    private String realName;



}

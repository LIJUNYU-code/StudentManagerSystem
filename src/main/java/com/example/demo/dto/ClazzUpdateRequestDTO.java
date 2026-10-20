package com.example.demo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClazzUpdateRequestDTO {

    @Size(min = 2, max = 100, message = "姓名长度 2~100 个字符")
    private String name;

    private String grade;

    private Integer teacherId;

}

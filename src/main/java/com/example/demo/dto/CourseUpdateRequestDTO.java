package com.example.demo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseUpdateRequestDTO {
    @Size(min = 2, max = 100, message = "课程长度 2~100 个字符")
    private String name;

    private BigDecimal credit;

    private Long teacherId;
}

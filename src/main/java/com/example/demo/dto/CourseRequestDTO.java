package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseRequestDTO {

    @NotBlank
    @Size(max = 100)
    private String name;

    @DecimalMin("0.5")
    @DecimalMax("10")
    private BigDecimal credit;

    private Long teacherId;

}

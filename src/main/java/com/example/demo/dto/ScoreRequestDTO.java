package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data

public class ScoreRequestDTO {
    @NotNull
    private Long enrollmentId;
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal value;
    @NotNull
    private String examType;

}

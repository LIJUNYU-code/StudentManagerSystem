package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class EnrollmentRequestDTO {

    @NotNull
    @Positive
    private Long studentId;
    @NotNull
    @Positive
    private Long courseId;

}

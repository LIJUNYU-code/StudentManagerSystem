package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Schema(description = "学生选课请求")
@Data
public class EnrollmentRequestDTO {

    @Schema(description = "学生 id", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "学生 id 不能为空")
    @Positive(message = "学生 id 必须是正整数")
    private Long studentId;

    @Schema(description = "课程 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程 id 不能为空")
    @Positive(message = "课程 id 必须是正整数")
    private Long courseId;

}

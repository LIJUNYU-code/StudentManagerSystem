package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "更新课程请求：所有字段可选，null 表示不修改该字段")
@Data
public class CourseUpdateRequestDTO {

    @Schema(description = "课程名，改成已被其他课程占用的名字返回 409", example = "线性代数")
    @Size(min = 2, max = 100, message = "课程名长度 2~100 个字符")
    private String name;

    @Schema(description = "学分", example = "3.0")
    private BigDecimal credit;

    @Schema(description = "授课教师的用户 id", example = "2")
    private Long teacherId;
}

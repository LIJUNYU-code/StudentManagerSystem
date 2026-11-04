package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "新建课程请求")
@Data
public class CourseRequestDTO {

    @Schema(description = "课程名，最长 100 字符，全局唯一，重名返回 409", example = "高等数学")
    @NotBlank(message = "课程名不能为空")
    @Size(max = 100, message = "课程名最长 100 个字符")
    private String name;

    @Schema(description = "学分，0.5~10", example = "4.0")
    @DecimalMin(value = "0.5", message = "学分最小为 0.5")
    @DecimalMax(value = "10", message = "学分最大为 10")
    private BigDecimal credit;

    @Schema(description = "授课教师的用户 id，可选", example = "2")
    private Long teacherId;

}

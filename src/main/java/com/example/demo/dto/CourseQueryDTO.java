package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Schema(description = "课程分页查询条件")
@Data
public class CourseQueryDTO {

    @Schema(description = "关键词，模糊匹配课程名，不传则不过滤", example = "数学")
    private String keyword;

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1,message = "页码最小数为 1")
    private Integer page = 1;

    @Schema(description = "每页条数，上限 100", example = "10", defaultValue = "10")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100 , message = "每页条数最大数为100")
    private Integer pageSize = 10;

}

package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "成绩分页查询条件")
@Data
public class ScoreQueryDTO {

    @Schema(description = "课程 id，必填", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1,message = "页码最小数为 1")
    private Integer page = 1;

    @Schema(description = "每页条数，上限 100", example = "10", defaultValue = "10")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100 , message = "每页条数最大数为100")
    private Integer pageSize = 10;
}

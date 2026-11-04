package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 点名名单查询参数。
 * <p>
 * sessionNo 给了默认值 1：单节课场景前端不用传。
 * 日期必填——不传日期就不知道该点哪天的名，那不是"查全部"的意思。
 */
@Schema(description = "点名名单查询条件")
@Data
public class AttendanceQueryDTO {

    @Schema(description = "课程 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @Schema(description = "上课日期，格式 YYYY-MM-DD", example = "2026-11-04", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上课日期不能为空")
    private LocalDate attendDate;

    @Schema(description = "第几节课，1~20，默认 1", example = "1", defaultValue = "1")
    @Min(value = 1, message = "节次最小为 1")
    @Max(value = 20, message = "节次最大为 20")
    private Integer sessionNo = 1;
}

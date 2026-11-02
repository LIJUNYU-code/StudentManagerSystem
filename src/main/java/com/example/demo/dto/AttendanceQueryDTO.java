package com.example.demo.dto;

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
@Data
public class AttendanceQueryDTO {

    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @NotNull(message = "上课日期不能为空")
    private LocalDate attendDate;

    @Min(value = 1, message = "节次最小为 1")
    @Max(value = 20, message = "节次最大为 20")
    private Integer sessionNo = 1;
}

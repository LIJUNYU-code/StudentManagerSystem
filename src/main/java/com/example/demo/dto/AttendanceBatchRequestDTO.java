package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量点名请求：一次提交全班记录。
 * <p>
 * 为什么要批量：30 个学生点 30 次接口，每次都要过一遍 JWT 校验、开一次连接，
 * 而且中途失败会留下"点了一半"的状态。一次请求 + 一个事务，要么全成要么全不成。
 */
@Schema(description = "批量点名请求：一次提交某课程某天某节次的全班考勤")
@Data
public class AttendanceBatchRequestDTO {

    @Schema(description = "课程 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @Schema(description = "上课日期，格式 YYYY-MM-DD", example = "2026-11-04", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上课日期不能为空")
    private LocalDate attendDate;

    /** 第几节课，默认 1（单节课场景不用关心） */
    @Schema(description = "第几节课，1~20，默认 1", example = "1", defaultValue = "1")
    @Min(value = 1, message = "节次最小为 1")
    @Max(value = 20, message = "节次最大为 20")
    private Integer sessionNo = 1;

    /**
     * 学生考勤列表。
     * <p>
     * {@code List<@Valid Item>} 把校验注解标在<b>类型参数</b>上，这是 Hibernate Validator 6.1+
     * 的推荐写法。老写法 {@code @Valid List<Item> items} 虽然还能用，但会打印弃用警告
     * （"Using @Valid on a container is deprecated"），而且语义上不准确：
     * 要校验的是"列表里的每个元素"，不是"列表这个容器"。
     * <p>
     * 无论哪种写法，漏掉它都会让 Item 上的 @NotNull/@Max 静默失效——
     * 非法状态值能一路穿过 Controller 进 Service，这是嵌套校验最常见的坑。
     */
    @Schema(description = "学生考勤列表，不能为空", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "考勤列表不能为空")
    private List<@Valid Item> items;

    @Schema(description = "单个学生的考勤项")
    @Data
    public static class Item {

        @Schema(description = "学生 id", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "学生 id 不能为空")
        private Long studentId;

        /** 0-出勤 1-迟到 2-早退 3-请假 4-旷课 */
        @Schema(description = "出勤状态：0=出勤，1=迟到，2=早退，3=请假，4=旷课",
                allowableValues = {"0", "1", "2", "3", "4"}, example = "0")
        @NotNull(message = "出勤状态不能为空")
        @Min(value = 0, message = "出勤状态只能是 0-4")
        @Max(value = 4, message = "出勤状态只能是 0-4")
        private Integer status;

        @Schema(description = "备注，最多 200 字，可选", example = "病假")
        @Size(max = 200, message = "备注最多 200 字")
        private String remark;
    }
}

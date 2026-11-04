package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "录入成绩请求")
@Data
public class ScoreRequestDTO {

    @Schema(description = "选课记录 id（不是学生 id、也不是课程 id）。先调 /enrollments 查到选课记录的 id", example = "1")
    @NotNull(message = "选课记录 id 不能为空")
    private Long enrollmentId;

    @Schema(description = "分数，0~100，可带一位小数", example = "87.5")
    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0", message = "分数最小为 0")
    @DecimalMax(value = "100", message = "分数最大为 100")
    private BigDecimal value;

    /**
     * 考试类型。
     * <p>
     * <b>必须加白名单校验</b>：这个字段原来只要求非空，任何字符串都能写进库。
     * 实测库里同时存在 {@code final}/{@code midterm}（新数据）和
     * {@code 期末}/{@code 期中}/{@code 平时}（早期手工测试数据）两套值——
     * 前端按一套 key 做翻译，另一套就显示成原文或空白。
     * 这种"脏数据"一旦积累，后续做统计分组、成绩单排序时全是坑。
     * <p>
     * 用 @Pattern 而不是自建枚举，是因为库里已经是 VARCHAR：
     * 改成 Java 枚举需要同时改实体、Mapper、前端，收益不足以抵消改动风险。
     * 白名单在入口挡住新脏数据，存量数据由迁移脚本单独处理（见 V11）。
     */
    @Schema(description = "考试类型，只能填 final（期末）/ midterm（期中）/ makeup（补考）",
            allowableValues = {"final", "midterm", "makeup"}, example = "final")
    @NotNull(message = "考试类型不能为空")
    @Pattern(regexp = "^(final|midterm|makeup)$",
            message = "考试类型只能是 final / midterm / makeup")
    private String examType;
}

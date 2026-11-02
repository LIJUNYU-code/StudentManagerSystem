package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreRequestDTO {

    @NotNull
    private Long enrollmentId;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
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
    @NotNull(message = "考试类型不能为空")
    @Pattern(regexp = "^(final|midterm|makeup)$",
            message = "考试类型只能是 final / midterm / makeup")
    private String examType;
}

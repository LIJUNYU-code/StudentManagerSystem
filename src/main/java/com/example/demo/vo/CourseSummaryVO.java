package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 课程成绩概览（统计卡片用）。
 * <p>
 * 注意：必须有 @NoArgsConstructor —— 自定义 SQL（ScoreMapper.selectCourseSummary）
 * 走的是"反射 new + setter 赋值"的路径，只有全参构造器时 MyBatis 无法实例化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSummaryVO {

    /** 参与统计的成绩条数（不是学生人数——一人可能有多条期中/期末记录） */
    private Long count;

    /** 平均分，保留 1 位小数（与库表 decimal(5,1) 对齐） */
    private BigDecimal avg;

    /** 最高分 */
    private BigDecimal max;

    /** 最低分 */
    private BigDecimal min;

    /** 及格率 0~1，保留 4 位小数（前端乘 100 显示百分比） */
    private BigDecimal passRate;
}

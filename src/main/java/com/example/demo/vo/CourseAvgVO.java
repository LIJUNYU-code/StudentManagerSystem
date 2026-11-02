package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 班级内各课程平均分对比（折线/柱状图用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAvgVO {

    private Long courseId;

    /** 课程名 */
    private String courseName;

    /** 该课程在本班的平均分 */
    private BigDecimal avg;

    /** 参与统计的成绩条数（样本太少时图表参考价值低，前端可提示） */
    private Long count;
}

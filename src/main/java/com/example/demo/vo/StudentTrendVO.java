package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 学生个人成绩趋势的一项（课程名 + 考试类型 + 分数）。
 * <p>
 * 为什么单独建类而不是复用 ScoreResponse：ScoreResponse 带 enrollmentId、
 * 学生姓名学号等一堆字段，图表只需要三个值。接口返回的字段越少，前端越好用，
 * 契约也越稳定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentTrendVO {

    private String courseName;

    /** 考试类型：final / midterm / makeup（数据库存英文，前端负责翻译成中文） */
    private String examType;

    private BigDecimal value;
}

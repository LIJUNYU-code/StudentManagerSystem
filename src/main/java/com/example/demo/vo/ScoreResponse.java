package com.example.demo.vo;

import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩返回结构（对外，不含任何敏感字段）。
 * <p>
 * 注意：必须有 @NoArgsConstructor —— 自定义 SQL（ScoreMapper.selectPageByCourse）
 * 走的是"反射 new + setter 赋值"的路径，只有全参构造器时 MyBatis 无法实例化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResponse {
    private Long id;

    private Long enrollmentId;

    private String studentName;

    private String studentNo;

    /** 课程名（学生端"我的成绩"需要，之前缺失导致前端只能显示学号） */
    private String courseName;

    private BigDecimal value;

    private String examType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static ScoreResponse from(Score score, Student student) {
        if (score == null)
            return null;

        return ScoreResponse.builder()
                .id(score.getId())
                .enrollmentId(score.getEnrollmentId())
                .studentName(student == null ? null : student.getName())
                .studentNo(student == null ? null : student.getStudentNo())
                .value(score.getValue())
                .examType(score.getExamType())
                .createTime(score.getCreateTime())
                .updateTime(score.getUpdateTime())
                .build();
    }
}

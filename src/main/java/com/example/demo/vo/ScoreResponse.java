package com.example.demo.vo;

import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class ScoreResponse {
    private Long id;

    private Long enrollmentId;

    private String studentName;

    private String studentNo;

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

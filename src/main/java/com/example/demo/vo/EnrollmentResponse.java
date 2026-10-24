package com.example.demo.vo;

import com.example.demo.entity.Enrollment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnrollmentResponse {

    private Long id;

    private Long studentId;

    private Long courseId;

    private String studentName;

    private String courseName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


    public static EnrollmentResponse from(Enrollment enrollment,String studentname,String coursename) {
        if (enrollment == null) {
            return null;
        }
        return EnrollmentResponse.builder()
                .id(enrollment.getId())
                .studentId(enrollment.getStudentId())
                .courseId(enrollment.getCourseId())
                .studentName(studentname)
                .courseName(coursename)
                .createTime(enrollment.getCreateTime())
                .updateTime(enrollment.getUpdateTime())
                .build();

    }
}

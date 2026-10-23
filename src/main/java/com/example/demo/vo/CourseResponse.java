package com.example.demo.vo;


import com.example.demo.entity.Course;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CourseResponse {

    private Long id;

    private String name;

    private BigDecimal credit;

    private Long teacherId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


    public static CourseResponse from(Course course) {
        if (course == null) {
            return null;
        }
        return CourseResponse.builder().id(course.getId())
                .name(course.getName())
                .credit(course.getCredit())
                .teacherId(course.getTeacherId())
                .createTime(course.getCreateTime())
                .updateTime(course.getUpdateTime())
                .build();
    }
}

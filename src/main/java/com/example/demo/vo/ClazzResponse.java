package com.example.demo.vo;

import com.example.demo.entity.Clazz;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClazzResponse {

    private Long id;

    private String name;

    private String grade;

    private Integer teacherId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static ClazzResponse from(Clazz c) {
        if (c == null) {
            return null;
        }
        return ClazzResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .grade(c.getGrade())
                .teacherId(c.getTeacherId())
                .createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime())
                .build();
    }


}

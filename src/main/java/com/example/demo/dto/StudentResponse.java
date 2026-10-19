package com.example.demo.dto;

import com.example.demo.entity.Student;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生信息响应 VO。
 * 由实体手动映射生成，password/deleted 字段永远不会出现在这里——
 * 这是对“接口不泄露密码哈希”的编译期保证（比 @JsonIgnore 更直白）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentResponse {
    private Long id;
    private String studentNo;
    private Long clazzId;
    private String name;
    private Integer gender;
    private Integer age;
    private String phone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 实体 → VO 映射（字段少，手动写比引 MapStruct 更轻） */
    public static StudentResponse from(Student s) {
        if (s == null) {
            return null;
        }
        return StudentResponse.builder()
                .id(s.getId())
                .studentNo(s.getStudentNo())
                .clazzId(s.getClazzId())
                .name(s.getName())
                .gender(s.getGender())
                .age(s.getAge())
                .phone(s.getPhone())
                .createTime(s.getCreateTime())
                .updateTime(s.getUpdateTime())
                .build();
    }
}

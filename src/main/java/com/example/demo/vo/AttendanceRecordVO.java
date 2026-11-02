package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生本人视角的考勤记录（「我的考勤」页面用）。
 * <p>
 * 与 AttendanceStudentVO 的区别：那个是「老师点名时看到的一行」（带学生姓名学号），
 * 这个是「学生自己看到的一行」（带课程名和日期）。字段完全不同，不该共用一个类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecordVO {

    private Long id;

    private Long courseId;

    private String courseName;

    private LocalDate attendDate;

    private Integer sessionNo;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;
}

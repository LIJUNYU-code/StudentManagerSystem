package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点名名单中的一行：学生信息 + 该节课的考勤状态。
 * <p>
 * attendanceId 为 null 表示「这节课还没给这个学生点名」——
 * 前端据此显示"未点名"并允许提交；有值则表示已点名，可以修改。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStudentVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    /** 考勤记录 id；null = 这节课还没点名 */
    private Long attendanceId;

    /** 出勤状态：0-出勤 1-迟到 2-早退 3-请假 4-旷课；null = 未点名 */
    private Integer status;

    private String remark;
}

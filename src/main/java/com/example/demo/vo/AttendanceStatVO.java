package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单个学生在一门课上的出勤统计。
 * <p>
 * 五种状态分别计数而不是只给一个"出勤率"：出勤率把迟到、请假、旷课混在一起，
 * 管理者看不出问题类型（一个天天迟到的学生和一个常请假的学生，出勤率可能一样）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatVO {

    private Long studentId;

    private String studentNo;

    private String studentName;

    /** 被点名总次数 */
    private Long total;

    /** 出勤（status=0） */
    private Long present;

    /** 迟到（status=1） */
    private Long late;

    /** 早退（status=2） */
    private Long earlyLeave;

    /** 请假（status=3） */
    private Long leaveCount;

    /** 旷课（status=4） */
    private Long absent;

    /** 出勤率 0~1（只算出勤，不含迟到/早退），4 位小数 */
    private BigDecimal attendanceRate;
}

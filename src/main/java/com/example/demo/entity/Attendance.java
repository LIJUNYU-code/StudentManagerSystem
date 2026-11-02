package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤记录。
 * <p>
 * <b>注意：本表没有 deleted 列，也没有 @TableLogic。</b>
 * 考勤是历史档案——"某天某人旷课"这件事发生过就不会消失，删掉是篡改历史。
 * 录错了应该改成正确状态（PUT 更新），而不是逻辑删除。
 * 这与 score 表保留 deleted 不同：成绩可以录错作废，考勤记录本身就是一个事实。
 * <p>
 * <b>为什么是 studentId + courseId 而不是 enrollmentId：</b>
 * enrollment 是「当前状态表」，退课物理删除（见 V6）。若考勤挂在 enrollment 上，
 * 学生一退课历史出勤全部变孤儿。详见 sql/V9__attendance.sql 的注释。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生 id（逻辑关联 student.id） */
    private Long studentId;

    /** 课程 id（逻辑关联 course.id） */
    private Long courseId;

    /** 上课日期（只有年月日，没有时分秒——点名是按天按节次的） */
    private LocalDate attendDate;

    /** 第几节课（同一天可能有多节），默认 1 */
    private Integer sessionNo;

    /** 出勤状态：0-出勤 1-迟到 2-早退 3-请假 4-旷课 */
    private Integer status;

    /** 备注（请假原因等） */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

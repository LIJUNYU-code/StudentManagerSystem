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
 * 请假申请。
 * <p>
 * <b>本表没有 deleted 列，也没有 @TableLogic</b>：请假记录是审批档案，
 * 撤销走 status=3（已撤销），不是删掉。删掉就查不到"这个学生上周申请过请假但被驳回了"。
 * <p>
 * <b>状态机</b>（Service 层校验，见 {@link com.example.demo.service.impl.LeaveServiceImpl}）：
 * <pre>
 *   0 待审核 ──approve──> 1 已通过   （终态）
 *   0 待审核 ──reject───> 2 已驳回   （终态）
 *   0 待审核 ──cancel───> 3 已撤销   （终态）
 * </pre>
 * 1/2/3 都是终态，不能再变。否则"已通过的请假被驳回"会让考勤统计错乱。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LeaveRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请学生 student.id */
    private Long studentId;

    /** 请假课程 course.id */
    private Long courseId;

    /** 请假开始日期 */
    private LocalDate startDate;

    /** 请假结束日期（含当天） */
    private LocalDate endDate;

    /** 请假理由 */
    private String reason;

    /** 状态：0-待审核 1-已通过 2-已驳回 3-已撤销 */
    private Integer status;

    /** 审批人 user.id；未审批时为 null */
    private Long reviewerId;

    /** 审批意见 */
    private String reviewRemark;

    /** 审批时间 */
    private LocalDateTime reviewTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

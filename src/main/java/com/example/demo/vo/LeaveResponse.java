package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 请假记录（列表和详情共用）。
 * <p>
 * 同时带 studentName/courseName/reviewerName 三个"冗余"字段：
 * 前端列表要显示"谁、哪门课、谁批的"，如果只返回 id，
 * 前端得为每一行再发三次请求（或后端做 N+1 查询）。
 * 在 Service 层用批量查 Map 一次性填好，是列表接口的标准做法。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponse {

    private Long id;

    private Long studentId;

    private String studentNo;

    private String studentName;

    private Long courseId;

    private String courseName;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 请假天数（含首尾），由 Service 算好，前端直接显示 */
    private Long days;

    private String reason;

    /** 0-待审核 1-已通过 2-已驳回 3-已撤销 */
    private Integer status;

    private Long reviewerId;

    private String reviewerName;

    private String reviewRemark;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;
}

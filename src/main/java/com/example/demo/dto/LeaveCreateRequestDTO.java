package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学生提交请假申请。
 * <p>
 * <b>studentId 不在这个 DTO 里</b>——身份必须来自 token，不能让客户端自己填。
 * 否则学生 A 可以替学生 B 提交请假（IDOR 越权）。
 * 这与 /scores/my、/attendance/my 的设计思路一致。
 */
@Data
public class LeaveCreateRequestDTO {

    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @NotBlank(message = "请假理由不能为空")
    @Size(max = 300, message = "请假理由最多 300 字")
    private String reason;
}

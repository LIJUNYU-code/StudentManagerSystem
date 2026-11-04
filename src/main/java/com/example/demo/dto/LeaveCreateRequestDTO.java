package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "学生提交请假申请。studentId 由服务端从 token 取，不接受客户端传")
@Data
public class LeaveCreateRequestDTO {

    @Schema(description = "请假对应的课程 id（审批通过后会把该课程这段日期的考勤记为请假）",
            example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @Schema(description = "请假开始日期，格式 YYYY-MM-DD", example = "2026-11-05", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @Schema(description = "请假结束日期，格式 YYYY-MM-DD。包含首尾两天，跨度上限 30 天",
            example = "2026-11-06", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    @Schema(description = "请假理由，不能为空，最多 300 字", example = "发烧就医",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请假理由不能为空")
    @Size(max = 300, message = "请假理由最多 300 字")
    private String reason;
}

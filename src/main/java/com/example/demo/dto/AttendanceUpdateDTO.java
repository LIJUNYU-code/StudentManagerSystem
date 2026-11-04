package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单条考勤修改（补录/纠错）。
 * <p>
 * 为什么只允许改 status 和 remark，不允许改 studentId/courseId/date：
 * 那三个字段构成唯一键，改它们等于"把这条记录搬到另一个格子"，
 * 语义上应该是删掉重录，而不是原地修改。
 */
@Schema(description = "单条考勤修改请求：只能改状态和备注")
@Data
public class AttendanceUpdateDTO {

    @Schema(description = "出勤状态：0=出勤，1=迟到，2=早退，3=请假，4=旷课",
            allowableValues = {"0", "1", "2", "3", "4"}, example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "出勤状态不能为空")
    @Min(value = 0, message = "出勤状态只能是 0-4")
    @Max(value = 4, message = "出勤状态只能是 0-4")
    private Integer status;

    @Schema(description = "备注，最多 200 字，可选", example = "迟到 10 分钟")
    @Size(max = 200, message = "备注最多 200 字")
    private String remark;
}

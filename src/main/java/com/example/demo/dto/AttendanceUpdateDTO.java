package com.example.demo.dto;

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
@Data
public class AttendanceUpdateDTO {

    @NotNull(message = "出勤状态不能为空")
    @Min(value = 0, message = "出勤状态只能是 0-4")
    @Max(value = 4, message = "出勤状态只能是 0-4")
    private Integer status;

    @Size(max = 200, message = "备注最多 200 字")
    private String remark;
}

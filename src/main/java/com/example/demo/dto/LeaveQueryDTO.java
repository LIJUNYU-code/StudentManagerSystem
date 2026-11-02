package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 请假列表查询参数。
 * <p>
 * 学生端和教师端共用这个 DTO，但语义不同：
 * <ul>
 *   <li>学生端：Service 会强制用 token 里的 studentId 覆盖，这里传什么都不生效</li>
 *   <li>教师端：可以按 status / courseId 筛选</li>
 * </ul>
 * 所以 studentId 字段**故意不放进来**——如果放进来，学生端就得写"忽略客户端传的值"，
 * 而那种"传了但不用"的字段最容易在后续重构时被误用成越权入口。
 */
@Data
public class LeaveQueryDTO {

    /** 状态筛选（0待审核 1已通过 2已驳回 3已撤销），null=全部 */
    @Min(value = 0, message = "状态只能是 0-3")
    @Max(value = 3, message = "状态只能是 0-3")
    private Integer status;

    /** 课程筛选，null=全部 */
    private Long courseId;

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 10;
}

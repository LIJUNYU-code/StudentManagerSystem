package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@Schema(description = "请假列表查询条件")
@Data
public class LeaveQueryDTO {

    /** 状态筛选（0待审核 1已通过 2已驳回 3已撤销），null=全部 */
    @Schema(description = "状态筛选：0=待审核，1=已通过，2=已驳回，3=已撤销。不传则查全部",
            allowableValues = {"0", "1", "2", "3"}, example = "0")
    @Min(value = 0, message = "状态只能是 0-3")
    @Max(value = 3, message = "状态只能是 0-3")
    private Integer status;

    /** 课程筛选，null=全部 */
    @Schema(description = "课程 id 筛选，不传则查全部课程", example = "1")
    private Long courseId;

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Schema(description = "每页条数，上限 100", example = "10", defaultValue = "10")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer size = 10;
}

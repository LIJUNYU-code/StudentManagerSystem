package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批请假（通过或驳回）。
 * <p>
 * <b>没有 status 字段</b>：通过还是驳回由<b>调用哪个接口</b>决定
 * （PUT /leave/{id}/approve 和 PUT /leave/{id}/reject）。
 * 如果让客户端传 status，就存在"传 1 但语义是驳回"这类不一致，
 * 而且校验起来要写一堆"status 只能是 1 或 2"。
 * 用两个接口把意图写进 URL，是最清晰的表达方式。
 */
@Data
public class LeaveReviewDTO {

    /** 审批意见（可选，驳回时建议填写原因） */
    @Size(max = 300, message = "审批意见最多 300 字")
    private String remark;

    /**
     * 审批人身份来自 token，不在这里。
     * 但这里有个"看似多余实则必要"的校验场景：
     * 如果教师 A 审批了自己提交的请假（教师也可能是学生？本项目不支持，但未来可能），
     * 那就需要判断 reviewerId != studentId。当前角色模型下不涉及，先不做。
     */
}

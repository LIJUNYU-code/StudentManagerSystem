package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.LeaveCreateRequestDTO;
import com.example.demo.dto.LeaveQueryDTO;
import com.example.demo.dto.LeaveReviewDTO;
import com.example.demo.service.LeaveService;
import com.example.demo.vo.LeaveResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请假申请与审批。
 *
 * <h3>权限设计</h3>
 * <ul>
 *   <li>学生：提交申请、查自己的、撤销自己的待审核申请</li>
 *   <li>教师/管理员：查全部、审批通过、审批驳回</li>
 * </ul>
 * 「查自己的」和「查全部」是两个不同接口（/leave/my 与 /leave），
 * 而不是一个接口按角色过滤——后者容易出现"角色判断写漏了就看到全部"的漏洞。
 * 两个接口各自的 @PreAuthorize 是硬边界。
 */
@Tag(name = "请假管理", description = "学生提交请假申请、教师审批。状态流转：0 待审核 → 1 已通过 / 2 已驳回 / 3 已撤销（三个终态）")
@RestController
@AllArgsConstructor
@RequestMapping("/leave")
public class LeaveController {

    private final LeaveService leaveService;

    /** 学生提交请假申请。studentId 由 Service 从 token 取。 */
    @Operation(summary = "提交请假申请", description = "请假天数上限 30 天；与已有待审核/已通过的请假区间重叠时返回 409")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public Result<LeaveResponse> create(@Valid @RequestBody LeaveCreateRequestDTO dto) {
        return Result.success(leaveService.create(dto));
    }

    /** 学生查自己的请假列表。 */
    @Operation(summary = "查询我的请假", description = "仅学生可用，身份来自 token，不接受 studentId 参数")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public Result<PageResult<LeaveResponse>> myPage(@Valid LeaveQueryDTO query) {
        return Result.success(leaveService.myPage(query));
    }

    /** 教师/管理员查全部请假（待审核排最前）。 */
    @Operation(summary = "分页查询全部请假", description = "教师/管理员使用，可按状态、课程筛选，待审核排最前")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<PageResult<LeaveResponse>> page(@Valid LeaveQueryDTO query) {
        return Result.success(leaveService.page(query));
    }

    /** 审批通过：0 → 1，并把区间内考勤写成「请假」。 */
    @Operation(summary = "审批通过", description = "状态 0 → 1，同时把请假区间内的考勤写成「请假」，避免老师点名时误记旷课。非待审核状态返回 400")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}/approve")
    public Result<LeaveResponse> approve(@PathVariable Long id,
                                         @RequestBody(required = false) LeaveReviewDTO dto) {
        return Result.success(leaveService.approve(id, dto));
    }

    /** 审批驳回：0 → 2。 */
    @Operation(summary = "审批驳回", description = "状态 0 → 2，不修改考勤（请假没批，该记旷课还是记旷课）。非待审核状态返回 400")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}/reject")
    public Result<LeaveResponse> reject(@PathVariable Long id,
                                        @RequestBody(required = false) LeaveReviewDTO dto) {
        return Result.success(leaveService.reject(id, dto));
    }

    /** 学生撤销自己的待审核申请：0 → 3。 */
    @Operation(summary = "撤销请假申请", description = "状态 0 → 3。只能撤销自己的申请（否则 403），非待审核状态返回 400")
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        leaveService.cancel(id);
        return Result.success();
    }
}

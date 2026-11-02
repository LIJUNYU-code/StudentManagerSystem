package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.LeaveCreateRequestDTO;
import com.example.demo.dto.LeaveQueryDTO;
import com.example.demo.dto.LeaveReviewDTO;
import com.example.demo.vo.LeaveResponse;

/**
 * 请假申请与审批。
 *
 * <h3>状态机（唯一允许的流转）</h3>
 * <pre>
 *   0 待审核 ──approve──> 1 已通过   （终态，同时把考勤写成「请假」）
 *   0 待审核 ──reject───> 2 已驳回   （终态）
 *   0 待审核 ──cancel───> 3 已撤销   （终态，仅本人）
 * </pre>
 * 1/2/3 都是终态。任何从终态出发的流转都抛 400——
 * 因为"已通过的请假被驳回"会让已经写进考勤的「请假」状态和申请记录不一致，
 * 统计就错了，而且这种错在界面上看不出来。
 */
public interface LeaveService {

    /** 学生提交申请。身份从 token 取，日期区间不能重叠（同一门课）。 */
    LeaveResponse create(LeaveCreateRequestDTO dto);

    /** 学生查自己的请假列表。 */
    PageResult<LeaveResponse> myPage(LeaveQueryDTO query);

    /** 教师/管理员查全部请假（可按状态、课程筛选）。 */
    PageResult<LeaveResponse> page(LeaveQueryDTO query);

    /** 审批通过：0 → 1，并把区间内考勤写成「请假」。 */
    LeaveResponse approve(Long id, LeaveReviewDTO dto);

    /** 审批驳回：0 → 2。 */
    LeaveResponse reject(Long id, LeaveReviewDTO dto);

    /** 学生撤销自己的待审核申请：0 → 3。 */
    void cancel(Long id);
}

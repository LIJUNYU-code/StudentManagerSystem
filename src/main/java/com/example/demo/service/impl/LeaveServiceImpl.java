package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.LeaveCreateRequestDTO;
import com.example.demo.dto.LeaveQueryDTO;
import com.example.demo.dto.LeaveReviewDTO;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.AttendanceMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.mapper.LeaveRequestMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.LeaveService;
import com.example.demo.vo.LeaveResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    /** 单次请假最长天数。防止"请一年假"这种明显误操作把考勤表刷满。 */
    private static final long MAX_LEAVE_DAYS = 30;

    /** 状态常量（与 V12 建表注释、前端翻译表保持一致） */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int STATUS_CANCELLED = 3;

    /** 考勤状态：请假 */
    private static final int ATTENDANCE_LEAVE = 3;

    private final LeaveRequestMapper leaveMapper;
    private final AttendanceMapper attendanceMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;

    // ==================== 学生提交 ====================

    @Override
    @Transactional
    public LeaveResponse create(LeaveCreateRequestDTO dto) {
        Long studentId = currentStudentId();

        // ① 日期合法性：起止顺序 + 不能超过 30 天
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(400, "结束日期不能早于开始日期");
        }
        long days = ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;
        if (days > MAX_LEAVE_DAYS) {
            throw new BusinessException(400, "单次请假不能超过 " + MAX_LEAVE_DAYS + " 天");
        }

        // ② 课程必须存在，且该学生确实选了这门课
        if (courseMapper.selectById(dto.getCourseId()) == null) {
            throw new BusinessException(404, "课程不存在");
        }
        Long enrolled = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getCourseId, dto.getCourseId()));
        if (enrolled == null || enrolled == 0) {
            throw new BusinessException(400, "你未选修该课程，无法请假");
        }

        // ③ 区间重叠检查：同一门课的待审核/已通过申请不能重叠。
        //    为什么只查这两种状态：已驳回/已撤销的申请不该占位，
        //    否则学生被驳回一次就再也请不了同一段时间的假了。
        //    重叠判断用标准公式：新起点 <= 旧终点 && 新终点 >= 旧起点
        Long overlap = leaveMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStudentId, studentId)
                .eq(LeaveRequest::getCourseId, dto.getCourseId())
                .in(LeaveRequest::getStatus, STATUS_PENDING, STATUS_APPROVED)
                .le(LeaveRequest::getStartDate, dto.getEndDate())
                .ge(LeaveRequest::getEndDate, dto.getStartDate()));
        if (overlap != null && overlap > 0) {
            throw new BusinessException(409, "该时间段已有请假申请（待审核或已通过），不能重复提交");
        }

        LeaveRequest entity = LeaveRequest.builder()
                .studentId(studentId)
                .courseId(dto.getCourseId())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .reason(dto.getReason())
                .status(STATUS_PENDING)
                .build();
        leaveMapper.insert(entity);

        return toResponse(leaveMapper.selectById(entity.getId()));
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<LeaveResponse> myPage(LeaveQueryDTO query) {
        Long studentId = currentStudentId();
        // 学生端强制用 token 里的 id，忽略客户端可能传的任何筛选——
        // 这样就不存在"改个参数看别人请假"的越权路径。
        return pageInternal(query, studentId);
    }

    @Override
    public PageResult<LeaveResponse> page(LeaveQueryDTO query) {
        return pageInternal(query, null);
    }

    private PageResult<LeaveResponse> pageInternal(LeaveQueryDTO query, Long forceStudentId) {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<LeaveRequest>()
                .eq(forceStudentId != null, LeaveRequest::getStudentId, forceStudentId)
                .eq(query.getStatus() != null, LeaveRequest::getStatus, query.getStatus())
                .eq(query.getCourseId() != null, LeaveRequest::getCourseId, query.getCourseId())
                // 待审核的排最前面（教师最关心），然后按申请时间倒序
                .orderByAsc(LeaveRequest::getStatus)
                .orderByDesc(LeaveRequest::getCreateTime);

        Page<LeaveRequest> page = leaveMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<LeaveResponse> records = toResponseList(page.getRecords());
        return new PageResult<>(page.getTotal(), records, page.getCurrent(), page.getSize());
    }

    // ==================== 审批 ====================

    @Override
    @Transactional
    public LeaveResponse approve(Long id, LeaveReviewDTO dto) {
        LeaveRequest leave = requirePending(id, "审批");
        Long reviewerId = currentUserId();

        LeaveRequest patch = LeaveRequest.builder()
                .id(id)
                .status(STATUS_APPROVED)
                .reviewerId(reviewerId)
                .reviewRemark(dto == null ? null : dto.getRemark())
                .reviewTime(LocalDateTime.now())
                .build();
        leaveMapper.updateById(patch);

        // 关键：审批通过后把区间内的考勤写成「请假」
        syncAttendance(leave);

        return toResponse(leaveMapper.selectById(id));
    }

    @Override
    @Transactional
    public LeaveResponse reject(Long id, LeaveReviewDTO dto) {
        requirePending(id, "驳回");

        LeaveRequest patch = LeaveRequest.builder()
                .id(id)
                .status(STATUS_REJECTED)
                .reviewerId(currentUserId())
                .reviewRemark(dto == null ? null : dto.getRemark())
                .reviewTime(LocalDateTime.now())
                .build();
        leaveMapper.updateById(patch);

        // 驳回不改考勤：请假没批，学生该记旷课还是旷课
        return toResponse(leaveMapper.selectById(id));
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        LeaveRequest leave = leaveMapper.selectById(id);
        if (leave == null) {
            throw new BusinessException(404, "请假申请不存在");
        }
        Long studentId = currentStudentId();
        // 越权检查：只能撤销自己的申请。
        // 这里必须用 403 而不是 404——如果返回 404，攻击者无法区分
        // "记录不存在"和"记录存在但不是你的"，但仍然不该让它看到别人的数据。
        if (!studentId.equals(leave.getStudentId())) {
            throw new BusinessException(403, "只能撤销自己的请假申请");
        }
        if (leave.getStatus() != STATUS_PENDING) {
            throw new BusinessException(400, "只能撤销待审核的申请（当前状态：" + statusLabel(leave.getStatus()) + "）");
        }

        LeaveRequest patch = LeaveRequest.builder()
                .id(id)
                .status(STATUS_CANCELLED)
                .build();
        leaveMapper.updateById(patch);
    }

    // ==================== 考勤联动 ====================

    /**
     * 把请假区间内的考勤写成「请假」。
     *
     * <h3>策略：已存在的记录改写状态 + 不存在的日期补建</h3>
     * 为什么不只改已存在的：
     * 学生通常是<b>提前</b>请假（下周要请假，今天提交）。这时未来那些天还没有考勤记录
     * （老师还没点名）。如果只改已有记录，等老师那天打开点名名单时，
     * 这个学生显示"未点名"，老师并不知道他请假了，很可能随手点成旷课——
     * 请假审批就白批了。所以必须补建记录，让老师一点开名单就看到"请假"。
     *
     * <h3>已知副作用（诚实记录）</h3>
     * 如果请假区间里包含<b>没排课的日子</b>（比如周末），也会补建一条 status=3 的记录。
     * 这会让"出勤总次数"分母变大，出勤率略微偏低。
     * 为什么接受这个副作用：
     * <ul>
     *   <li>本项目没有"课表"实体，无法判断哪天有课——要做对必须先建 course_schedule 表</li>
     *   <li>出勤率略微偏低比"请假批了但记成旷课"要轻得多</li>
     *   <li>补建只在审批那一刻发生一次，不会持续污染</li>
     * </ul>
     * 真要修，正确做法是引入课表；在那之前，宁可有这个可解释的偏差。
     *
     * <h3>节次处理</h3>
     * 已存在的记录：<b>该日期所有节次</b>都改成请假（请假是按天的，不分第几节）。
     * 不存在的记录：只补 session_no=1 一条，避免一天凭空造出多条。
     */
    private void syncAttendance(LeaveRequest leave) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = leave.getStartDate(); !d.isAfter(leave.getEndDate()); d = d.plusDays(1)) {
            dates.add(d);
        }

        // 一次查出区间内该学生该课程的所有已有记录，避免逐天查库（N+1）
        List<Attendance> existing = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getStudentId, leave.getStudentId())
                .eq(Attendance::getCourseId, leave.getCourseId())
                .between(Attendance::getAttendDate, leave.getStartDate(), leave.getEndDate()));

        Map<LocalDate, List<Attendance>> byDate = existing.stream()
                .collect(Collectors.groupingBy(Attendance::getAttendDate));

        for (LocalDate date : dates) {
            List<Attendance> dayRecords = byDate.get(date);
            if (dayRecords == null || dayRecords.isEmpty()) {
                // 没有记录：补建一条（让老师打开名单就看到「请假」）
                attendanceMapper.insert(Attendance.builder()
                        .studentId(leave.getStudentId())
                        .courseId(leave.getCourseId())
                        .attendDate(date)
                        .sessionNo(1)
                        .status(ATTENDANCE_LEAVE)
                        .remark("请假（审批通过）")
                        .build());
            } else {
                // 已有记录：所有节次都改成请假
                for (Attendance a : dayRecords) {
                    if (a.getStatus() != null && a.getStatus() == ATTENDANCE_LEAVE) {
                        continue; // 已经是请假，不用再写一次
                    }
                    attendanceMapper.updateById(Attendance.builder()
                            .id(a.getId())
                            .status(ATTENDANCE_LEAVE)
                            .remark("请假（审批通过）")
                            .build());
                }
            }
        }
    }

    // ==================== 内部工具 ====================

    /** 取出待审核的申请，不存在抛 404，非待审核抛 400。 */
    private LeaveRequest requirePending(Long id, String action) {
        LeaveRequest leave = leaveMapper.selectById(id);
        if (leave == null) {
            throw new BusinessException(404, "请假申请不存在");
        }
        if (leave.getStatus() != STATUS_PENDING) {
            throw new BusinessException(400,
                    "只能" + action + "待审核的申请（当前状态：" + statusLabel(leave.getStatus()) + "）");
        }
        return leave;
    }

    /** 批量组装 VO：一次查学生、一次查课程、一次查审批人，避免逐行查库。 */
    private List<LeaveResponse> toResponseList(List<LeaveRequest> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = list.stream().map(LeaveRequest::getStudentId).distinct().toList();
        List<Long> courseIds = list.stream().map(LeaveRequest::getCourseId).distinct().toList();
        List<Long> reviewerIds = list.stream()
                .map(LeaveRequest::getReviewerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        // 用 HashMap 手动 put 而不是 Collectors.toMap：后者对 null value 会抛 NPE
        Map<Long, Student> studentById = new HashMap<>();
        for (Student s : studentMapper.selectBatchIds(studentIds)) {
            studentById.put(s.getId(), s);
        }
        Map<Long, String> courseNameById = new HashMap<>();
        for (Course c : courseMapper.selectBatchIds(courseIds)) {
            courseNameById.put(c.getId(), c.getName());
        }
        Map<Long, String> reviewerNameById = new HashMap<>();
        if (!reviewerIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(reviewerIds)) {
                reviewerNameById.put(u.getId(), u.getRealName());
            }
        }

        return list.stream().map(l -> {
            Student s = studentById.get(l.getStudentId());
            return LeaveResponse.builder()
                    .id(l.getId())
                    .studentId(l.getStudentId())
                    .studentNo(s == null ? null : s.getStudentNo())
                    .studentName(s == null ? null : s.getName())
                    .courseId(l.getCourseId())
                    .courseName(courseNameById.get(l.getCourseId()))
                    .startDate(l.getStartDate())
                    .endDate(l.getEndDate())
                    .days(ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1)
                    .reason(l.getReason())
                    .status(l.getStatus())
                    .reviewerId(l.getReviewerId())
                    .reviewerName(l.getReviewerId() == null ? null : reviewerNameById.get(l.getReviewerId()))
                    .reviewRemark(l.getReviewRemark())
                    .reviewTime(l.getReviewTime())
                    .createTime(l.getCreateTime())
                    .build();
        }).toList();
    }

    private LeaveResponse toResponse(LeaveRequest leave) {
        if (leave == null) {
            return null;
        }
        return toResponseList(List.of(leave)).get(0);
    }

    /** 当前登录学生的 id（来自 token）。 */
    private Long currentStudentId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Long id)) {
            throw new BusinessException(401, "未登录");
        }
        return id;
    }

    /** 当前登录用户（教师/管理员）的 id。 */
    private Long currentUserId() {
        return currentStudentId(); // 两者在 token 里都是 subject=id，取法相同
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING -> "待审核";
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已驳回";
            case STATUS_CANCELLED -> "已撤销";
            default -> "未知";
        };
    }
}

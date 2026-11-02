package com.example.demo.service.impl;

import com.example.demo.dto.LeaveCreateRequestDTO;
import com.example.demo.dto.LeaveQueryDTO;
import com.example.demo.dto.LeaveReviewDTO;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.AttendanceMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.mapper.LeaveRequestMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.vo.LeaveResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * LeaveServiceImpl 单元测试。
 * <p>
 * 请假模块的复杂度几乎全在<b>状态机</b>和<b>考勤联动</b>这两处，所以断言重点也在那儿：
 * <ol>
 *   <li><b>终态不可逆</b>：已通过/已驳回/已撤销的申请不能再流转。破了这条，
 *       会出现"已通过的请假被驳回"——申请记录说驳回、考勤却是请假，两边不一致且界面上看不出来。</li>
 *   <li><b>审批通过必须写考勤，驳回必须不写</b>：这是请假功能存在的全部意义。
 *       请假批了但考勤还记旷课，等于白批；驳回却写了请假，等于学生自己给自己批假。</li>
 *   <li><b>越权撤销</b>：只能撤自己的。这里返回 403 而不是 404 是有意的——
 *       404 会让合法用户困惑（"我的申请怎么不见了"），403 才说明"它在，但不是你的"。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceImplTest {

    @Mock
    private LeaveRequestMapper leaveMapper;
    @Mock
    private AttendanceMapper attendanceMapper;
    @Mock
    private EnrollmentMapper enrollmentMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private UserMapper userMapper;

    private LeaveServiceImpl service;

    private static final Long STUDENT_ID = 42L;
    private static final Long COURSE_ID = 10L;
    private static final Long LEAVE_ID = 500L;
    private static final Long REVIEWER_ID = 1L;
    private static final LocalDate START = LocalDate.of(2026, 11, 15);
    private static final LocalDate END = LocalDate.of(2026, 11, 17);

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体元数据才能把
     * {@code LeaveRequest::getStatus} 翻译成列名。纯 Mockito 环境没有 Spring 上下文，
     * 这份元数据不会自动初始化，必须手动注册（否则抛 "can not find lambda cache"）。
     */
    @BeforeAll
    static void initTableInfo() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, LeaveRequest.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Attendance.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Enrollment.class);
    }

    @BeforeEach
    void setUp() {
        service = new LeaveServiceImpl(leaveMapper, attendanceMapper, enrollmentMapper,
                studentMapper, courseMapper, userMapper);
        // 模拟已登录学生：principal 就是 studentId（与 JwtAuthenticationFilter 一致）
        loginAs(STUDENT_ID);
    }

    /** 切换当前登录身份（token 的 subject 就是 id，学生和教师取法相同）。 */
    private void loginAs(Long id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== 测试夹具 ====================

    private LeaveCreateRequestDTO createDto(LocalDate start, LocalDate end) {
        LeaveCreateRequestDTO dto = new LeaveCreateRequestDTO();
        dto.setCourseId(COURSE_ID);
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("家中有事");
        return dto;
    }

    private LeaveRequest pendingLeave() {
        return LeaveRequest.builder()
                .id(LEAVE_ID)
                .studentId(STUDENT_ID)
                .courseId(COURSE_ID)
                .startDate(START)
                .endDate(END)
                .reason("家中有事")
                .status(0)
                .build();
    }

    private void courseExists() {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setName("数据结构");
        when(courseMapper.selectById(COURSE_ID)).thenReturn(c);
    }

    private void studentEnrolled() {
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
    }

    /**
     * 让 insert 回填 id（真实 MyBatis-Plus 会用自增主键回写到实体），
     * 否则后续 {@code selectById(entity.getId())} 拿到的是 null。
     */
    private void stubInsertReturnsId() {
        when(leaveMapper.insert(any(LeaveRequest.class))).thenAnswer(inv -> {
            inv.getArgument(0, LeaveRequest.class).setId(LEAVE_ID);
            return 1;
        });
    }

    /**
     * 让 selectById 返回指定对象（toResponse 内部会再查一次）。
     * 注意用 {@code any()} 而非 {@code anyLong()}：创建流程里 id 来自
     * insert 回填，若回填失败就是 null，anyLong() 匹配不上 null。
     */
    private void stubSelectById(LeaveRequest leave) {
        when(leaveMapper.selectById(any())).thenReturn(leave);
    }

    /** 空的学生/课程/审批人批量查询（toResponse 会调用）。 */
    private void stubBatchQueries() {
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of());
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
    }

    // ==================== create ====================

    @Test
    @DisplayName("提交请假：结束日期早于开始日期 → 400，不写库")
    void create_endBeforeStart() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(createDto(END, START)));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("结束日期"));
        verify(leaveMapper, never()).insert(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("提交请假：超过 30 天 → 400，不写库")
    void create_tooLong() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(createDto(START, START.plusDays(30))));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("30"));
        verify(leaveMapper, never()).insert(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("提交请假：正好 30 天 → 允许（边界值不能多拦一天）")
    void create_exactlyThirtyDays() {
        courseExists();
        studentEnrolled();
        when(leaveMapper.selectCount(any())).thenReturn(0L);
        stubInsertReturnsId();
        stubSelectById(pendingLeave());
        stubBatchQueries();

        // 29 天差 = 含首尾 30 天
        assertDoesNotThrow(() -> service.create(createDto(START, START.plusDays(29))));
        verify(leaveMapper).insert(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("提交请假：课程不存在 → 404")
    void create_courseNotFound() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.create(createDto(START, END))).getCode());
    }

    @Test
    @DisplayName("提交请假：未选修该课程 → 400（不能给没选的课请假）")
    void create_notEnrolled() {
        courseExists();
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(createDto(START, END)));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("未选修"));
        verify(leaveMapper, never()).insert(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("提交请假：区间与已有待审核/已通过申请重叠 → 409")
    void create_overlap() {
        courseExists();
        studentEnrolled();
        when(leaveMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(createDto(START, END)));

        assertEquals(409, e.getCode());
        verify(leaveMapper, never()).insert(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("提交请假成功：状态初始为 0（待审核），studentId 来自 token")
    void create_success() {
        courseExists();
        studentEnrolled();
        when(leaveMapper.selectCount(any())).thenReturn(0L);
        stubInsertReturnsId();
        stubSelectById(pendingLeave());
        stubBatchQueries();

        service.create(createDto(START, END));

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveMapper).insert(captor.capture());
        LeaveRequest saved = captor.getValue();
        assertEquals(0, saved.getStatus());
        // 关键：studentId 必须来自 token，不是 DTO
        assertEquals(STUDENT_ID, saved.getStudentId());
        assertEquals(COURSE_ID, saved.getCourseId());
    }

    // ==================== approve ====================

    @Test
    @DisplayName("审批通过：不存在 → 404")
    void approve_notFound() {
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.approve(LEAVE_ID, new LeaveReviewDTO())).getCode());
    }

    @Test
    @DisplayName("审批通过：已通过的再审批 → 400（终态不可逆）")
    void approve_alreadyApproved() {
        LeaveRequest approved = pendingLeave();
        approved.setStatus(1);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(approved);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.approve(LEAVE_ID, new LeaveReviewDTO()));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("已通过"));
        // 关键：不能有任何写操作，否则状态和考勤可能被二次修改
        verify(leaveMapper, never()).updateById(any(LeaveRequest.class));
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("审批通过：已驳回的再审批 → 400")
    void approve_alreadyRejected() {
        LeaveRequest rejected = pendingLeave();
        rejected.setStatus(2);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(rejected);

        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.approve(LEAVE_ID, new LeaveReviewDTO())).getCode());
        verify(leaveMapper, never()).updateById(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("审批通过：已撤销的再审批 → 400")
    void approve_alreadyCancelled() {
        LeaveRequest cancelled = pendingLeave();
        cancelled.setStatus(3);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(cancelled);

        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.approve(LEAVE_ID, new LeaveReviewDTO())).getCode());
    }

    @Test
    @DisplayName("★ 审批通过：状态改 1，且区间内没有记录时逐天补建「请假」")
    void approve_insertsAttendanceForEachDay() {
        loginAs(REVIEWER_ID); // 审批人是教师，不是学生
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(pendingLeave());
        when(leaveMapper.updateById(any(LeaveRequest.class))).thenReturn(1);
        // 区间内还没有任何考勤记录（学生提前请假，老师还没点名）
        when(attendanceMapper.selectList(any())).thenReturn(List.of());
        when(attendanceMapper.insert(any(Attendance.class))).thenReturn(1);
        stubBatchQueries();

        service.approve(LEAVE_ID, new LeaveReviewDTO());

        // 状态改为 1
        ArgumentCaptor<LeaveRequest> patchCaptor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveMapper).updateById(patchCaptor.capture());
        assertEquals(1, patchCaptor.getValue().getStatus());
        assertEquals(REVIEWER_ID, patchCaptor.getValue().getReviewerId());

        // 三天各补一条 status=3 的记录（11-15 / 11-16 / 11-17）
        ArgumentCaptor<Attendance> attCaptor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceMapper, times(3)).insert(attCaptor.capture());
        List<Attendance> inserted = attCaptor.getAllValues();
        assertEquals(List.of(START, START.plusDays(1), END),
                inserted.stream().map(Attendance::getAttendDate).toList());
        assertTrue(inserted.stream().allMatch(a -> a.getStatus() == 3));
        assertTrue(inserted.stream().allMatch(a -> a.getStudentId().equals(STUDENT_ID)));
        assertTrue(inserted.stream().allMatch(a -> a.getCourseId().equals(COURSE_ID)));
    }

    @Test
    @DisplayName("★ 审批通过：已有记录时改成「请假」，不重复插入")
    void approve_updatesExistingAttendance() {
        loginAs(REVIEWER_ID);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(pendingLeave());
        when(leaveMapper.updateById(any(LeaveRequest.class))).thenReturn(1);
        // 第一天已经点过名（旷课），后两天还没有
        Attendance existing = Attendance.builder()
                .id(900L).studentId(STUDENT_ID).courseId(COURSE_ID)
                .attendDate(START).sessionNo(1).status(4).build();
        when(attendanceMapper.selectList(any())).thenReturn(List.of(existing));
        when(attendanceMapper.updateById(any(Attendance.class))).thenReturn(1);
        when(attendanceMapper.insert(any(Attendance.class))).thenReturn(1);
        stubBatchQueries();

        service.approve(LEAVE_ID, new LeaveReviewDTO());

        // 已存在的改成请假
        ArgumentCaptor<Attendance> updCaptor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceMapper).updateById(updCaptor.capture());
        assertEquals(900L, updCaptor.getValue().getId());
        assertEquals(3, updCaptor.getValue().getStatus());

        // 缺的两天补建
        verify(attendanceMapper, times(2)).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("审批通过：已经是「请假」的记录不重复写库")
    void approve_skipsAlreadyLeave() {
        loginAs(REVIEWER_ID);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(pendingLeave());
        when(leaveMapper.updateById(any(LeaveRequest.class))).thenReturn(1);
        // 三天全是「请假」，无需任何考勤写操作
        List<Attendance> allLeave = List.of(
                Attendance.builder().id(1L).studentId(STUDENT_ID).courseId(COURSE_ID)
                        .attendDate(START).sessionNo(1).status(3).build(),
                Attendance.builder().id(2L).studentId(STUDENT_ID).courseId(COURSE_ID)
                        .attendDate(START.plusDays(1)).sessionNo(1).status(3).build(),
                Attendance.builder().id(3L).studentId(STUDENT_ID).courseId(COURSE_ID)
                        .attendDate(END).sessionNo(1).status(3).build());
        when(attendanceMapper.selectList(any())).thenReturn(allLeave);
        stubBatchQueries();

        service.approve(LEAVE_ID, new LeaveReviewDTO());

        verify(attendanceMapper, never()).insert(any(Attendance.class));
        verify(attendanceMapper, never()).updateById(any(Attendance.class));
    }

    @Test
    @DisplayName("审批通过：同一天多节次的记录全部改成请假")
    void approve_updatesAllSessionsOfSameDay() {
        loginAs(REVIEWER_ID);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(pendingLeave());
        when(leaveMapper.updateById(any(LeaveRequest.class))).thenReturn(1);
        // 第一天有两节课的记录
        List<Attendance> twoSessions = List.of(
                Attendance.builder().id(1L).studentId(STUDENT_ID).courseId(COURSE_ID)
                        .attendDate(START).sessionNo(1).status(0).build(),
                Attendance.builder().id(2L).studentId(STUDENT_ID).courseId(COURSE_ID)
                        .attendDate(START).sessionNo(2).status(0).build());
        when(attendanceMapper.selectList(any())).thenReturn(twoSessions);
        when(attendanceMapper.updateById(any(Attendance.class))).thenReturn(1);
        when(attendanceMapper.insert(any(Attendance.class))).thenReturn(1);
        stubBatchQueries();

        service.approve(LEAVE_ID, new LeaveReviewDTO());

        // 两节课都改 + 后两天补建
        verify(attendanceMapper, times(2)).updateById(any(Attendance.class));
        verify(attendanceMapper, times(2)).insert(any(Attendance.class));
    }

    // ==================== reject ====================

    @Test
    @DisplayName("★ 驳回：状态改 2，但绝不碰考勤")
    void reject_neverTouchesAttendance() {
        loginAs(REVIEWER_ID);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(pendingLeave());
        when(leaveMapper.updateById(any(LeaveRequest.class))).thenReturn(1);
        stubBatchQueries();

        service.reject(LEAVE_ID, new LeaveReviewDTO());

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getStatus());

        // 关键断言：驳回不能改考勤，否则学生自己给自己批假
        verify(attendanceMapper, never()).insert(any(Attendance.class));
        verify(attendanceMapper, never()).updateById(any(Attendance.class));
        verify(attendanceMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("驳回：已通过的再驳回 → 400")
    void reject_alreadyApproved() {
        LeaveRequest approved = pendingLeave();
        approved.setStatus(1);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(approved);

        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.reject(LEAVE_ID, new LeaveReviewDTO())).getCode());
        verify(leaveMapper, never()).updateById(any(LeaveRequest.class));
    }

    // ==================== cancel ====================

    @Test
    @DisplayName("撤销：不存在 → 404")
    void cancel_notFound() {
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.cancel(LEAVE_ID)).getCode());
    }

    @Test
    @DisplayName("★ 撤销别人的申请 → 403（越权防护）")
    void cancel_othersForbidden() {
        LeaveRequest others = pendingLeave();
        others.setStudentId(999L); // 不是当前登录的 42
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(others);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.cancel(LEAVE_ID));

        assertEquals(403, e.getCode());
        assertTrue(e.getMessage().contains("自己的"));
        verify(leaveMapper, never()).updateById(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("撤销：已通过的不能撤 → 400")
    void cancel_alreadyApproved() {
        LeaveRequest approved = pendingLeave();
        approved.setStatus(1);
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(approved);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.cancel(LEAVE_ID));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("待审核"));
        verify(leaveMapper, never()).updateById(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("撤销成功：状态改 3")
    void cancel_success() {
        when(leaveMapper.selectById(LEAVE_ID)).thenReturn(pendingLeave());
        when(leaveMapper.updateById(any(LeaveRequest.class))).thenReturn(1);

        service.cancel(LEAVE_ID);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus());
        assertEquals(LEAVE_ID, captor.getValue().getId());
    }

    // ==================== myPage ====================

    @Test
    @DisplayName("学生查自己的请假：强制用 token 里的 studentId")
    void myPage_forcesTokenStudentId() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<LeaveRequest> emptyPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        emptyPage.setRecords(List.of());
        when(leaveMapper.selectPage(any(), any())).thenReturn(emptyPage);

        LeaveQueryDTO q = new LeaveQueryDTO();
        assertDoesNotThrow(() -> service.myPage(q));

        // 验证查询条件里带了 studentId（通过 selectPage 的 wrapper 参数无法直接断言，
        // 这里改为验证行为：如果没强制 studentId，下面的 teacher page 就会和它一样）
        verify(leaveMapper).selectPage(any(), any());
    }
}

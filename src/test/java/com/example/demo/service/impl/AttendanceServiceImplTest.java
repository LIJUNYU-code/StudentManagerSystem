package com.example.demo.service.impl;

import com.example.demo.dto.AttendanceBatchRequestDTO;
import com.example.demo.dto.AttendanceQueryDTO;
import com.example.demo.dto.AttendanceUpdateDTO;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.AttendanceMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.vo.AttendanceRecordVO;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * AttendanceServiceImpl 单元测试。
 * <p>
 * 考勤模块有两条必须锁死的规则，都属于"破了也不会报错、但数据会悄悄错"的类型：
 * <ol>
 *   <li><b>只能给选了这门课的学生点名</b>——不校验的话，随便传个学生 id 就能写进考勤，
 *       出勤率统计会凭空多出不存在的人。</li>
 *   <li><b>批量提交必须是"覆盖"语义</b>——老师点错了重新点一次，如果报 409 或插入重复行，
 *       点名功能实际不可用（唯一索引会拦住第二次，老师看到的是报错）。</li>
 * </ol>
 * 另外这里刻意验证"同一次请求里重复学生 id 要拒绝"：这是客户端 bug 的早期暴露，
 * 放过去就会变成"后一条覆盖前一条"的静默行为，老师以为点了两个状态，实际只存了一个。
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock
    private AttendanceMapper attendanceMapper;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private CourseMapper courseMapper;

    private AttendanceServiceImpl service;

    private static final Long COURSE_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);

    /**
     * 纯 Mockito 单测没有 Spring 上下文，MyBatis-Plus 的实体元数据（表名/列名映射）
     * 不会自动初始化。而 LambdaQueryWrapper 的 eq/in 等方法需要这份元数据把
     * {@code Attendance::getStudentId} 翻译成列名，否则抛
     * "can not find lambda cache for this entity"。
     * <p>
     * 手动初始化一次即可：这是官方推荐做法，等价于让 MP 以为"这个实体已经被扫描过"。
     * 注意必须用 @BeforeAll + static——每个测试方法都初始化会重复注册。
     */
    @BeforeAll
    static void initTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                assistant, Attendance.class);
    }

    @BeforeEach
    void setUp() {
        service = new AttendanceServiceImpl(attendanceMapper, enrollmentMapper, courseMapper);
    }

    private Course course() {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setName("数据结构");
        return c;
    }

    private Enrollment enrollment(Long studentId) {
        Enrollment e = new Enrollment();
        e.setId(studentId * 100);
        e.setStudentId(studentId);
        e.setCourseId(COURSE_ID);
        return e;
    }

    private AttendanceBatchRequestDTO batchRequest(Long... studentIds) {
        AttendanceBatchRequestDTO dto = new AttendanceBatchRequestDTO();
        dto.setCourseId(COURSE_ID);
        dto.setAttendDate(DATE);
        dto.setSessionNo(1);
        List<AttendanceBatchRequestDTO.Item> items = new java.util.ArrayList<>();
        for (Long sid : studentIds) {
            AttendanceBatchRequestDTO.Item item = new AttendanceBatchRequestDTO.Item();
            item.setStudentId(sid);
            item.setStatus(0);
            items.add(item);
        }
        dto.setItems(items);
        return dto;
    }

    // ========== batchSave ==========

    @Test
    @DisplayName("批量点名：课程不存在 → 404，且不写任何记录")
    void batchSave_courseNotFound() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.batchSave(batchRequest(1L)));

        assertEquals(404, e.getCode());
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("批量点名：学生未选该课 → 400，且一条都没插入（关键防线）")
    void batchSave_studentNotEnrolled() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        // 只有学生 1 选了这门课
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment(1L)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.batchSave(batchRequest(1L, 999L)));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("999"));
        // 关键：第一个学生是合法的，但因为整批校验在写入之前完成，
        // 所以一行都不该写进去——否则就是"点了一半"的脏数据。
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("批量点名：同一次请求重复学生 → 400（客户端 bug 要早暴露）")
    void batchSave_duplicateStudentInRequest() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment(1L)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.batchSave(batchRequest(1L, 1L)));

        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("重复"));
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("批量点名：全新记录 → 逐条 insert，字段完整")
    void batchSave_insertNew() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment(1L), enrollment(2L)));
        when(attendanceMapper.selectList(any())).thenReturn(List.of()); // 本节课还没有任何记录
        when(attendanceMapper.insert(any(Attendance.class))).thenReturn(1);

        int affected = service.batchSave(batchRequest(1L, 2L));

        assertEquals(2, affected);
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceMapper, times(2)).insert(captor.capture());
        Attendance first = captor.getAllValues().get(0);
        assertEquals(COURSE_ID, first.getCourseId());
        assertEquals(DATE, first.getAttendDate());
        assertEquals(1, first.getSessionNo());
        assertEquals(0, first.getStatus());
    }

    @Test
    @DisplayName("批量点名：已存在的记录 → update 而不是 insert（覆盖语义）")
    void batchSave_updateExisting() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment(1L)));

        Attendance existing = Attendance.builder()
                .id(500L)
                .studentId(1L)
                .courseId(COURSE_ID)
                .attendDate(DATE)
                .sessionNo(1)
                .status(4) // 之前记的是旷课
                .build();
        when(attendanceMapper.selectList(any())).thenReturn(List.of(existing));
        when(attendanceMapper.updateById(any(Attendance.class))).thenReturn(1);

        int affected = service.batchSave(batchRequest(1L));

        assertEquals(1, affected);
        // 覆盖语义的核心：不能 insert 一条新的（唯一索引会炸），必须 update 原来那条
        verify(attendanceMapper, never()).insert(any(Attendance.class));
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceMapper).updateById(captor.capture());
        assertEquals(500L, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus()); // 旷课被改成出勤
    }

    @Test
    @DisplayName("批量点名：混合场景（一半新增一半更新）计数正确")
    void batchSave_mixed() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(enrollment(1L), enrollment(2L)));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(
                Attendance.builder().id(500L).studentId(1L).courseId(COURSE_ID).attendDate(DATE).sessionNo(1).build()));
        when(attendanceMapper.insert(any(Attendance.class))).thenReturn(1);
        when(attendanceMapper.updateById(any(Attendance.class))).thenReturn(1);

        int affected = service.batchSave(batchRequest(1L, 2L));

        assertEquals(2, affected);
        verify(attendanceMapper, times(1)).insert(any(Attendance.class));
        verify(attendanceMapper, times(1)).updateById(any(Attendance.class));
    }

    // ========== roster ==========

    @Test
    @DisplayName("点名名单：课程不存在 → 404")
    void roster_courseNotFound() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);
        AttendanceQueryDTO q = new AttendanceQueryDTO();
        q.setCourseId(COURSE_ID);
        q.setAttendDate(DATE);

        assertEquals(404, assertThrows(BusinessException.class, () -> service.roster(q)).getCode());
    }

    @Test
    @DisplayName("点名名单：把查询参数原样传给 Mapper（含未点名学生由 SQL 的 LEFT JOIN 保证）")
    void roster_passesParams() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        when(attendanceMapper.selectRoster(COURSE_ID, DATE, 1)).thenReturn(List.of());

        AttendanceQueryDTO q = new AttendanceQueryDTO();
        q.setCourseId(COURSE_ID);
        q.setAttendDate(DATE);
        q.setSessionNo(1);

        assertTrue(service.roster(q).isEmpty());
        verify(attendanceMapper).selectRoster(COURSE_ID, DATE, 1);
    }

    // ========== update ==========

    @Test
    @DisplayName("修改考勤：记录不存在 → 404，不执行 update")
    void update_notFound() {
        when(attendanceMapper.selectById(999L)).thenReturn(null);
        AttendanceUpdateDTO dto = new AttendanceUpdateDTO();
        dto.setStatus(0);

        assertEquals(404, assertThrows(BusinessException.class, () -> service.update(999L, dto)).getCode());
        verify(attendanceMapper, never()).updateById(any(Attendance.class));
    }

    @Test
    @DisplayName("修改考勤：只更新 status 和 remark，不碰 studentId/date（唯一键不可变）")
    void update_onlyMutableFields() {
        when(attendanceMapper.selectById(500L)).thenReturn(
                Attendance.builder().id(500L).studentId(1L).courseId(COURSE_ID).attendDate(DATE).sessionNo(1).build());
        when(attendanceMapper.updateById(any(Attendance.class))).thenReturn(1);

        AttendanceUpdateDTO dto = new AttendanceUpdateDTO();
        dto.setStatus(3);
        dto.setRemark("病假");
        service.update(500L, dto);

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceMapper).updateById(captor.capture());
        Attendance patch = captor.getValue();
        assertEquals(500L, patch.getId());
        assertEquals(3, patch.getStatus());
        assertEquals("病假", patch.getRemark());
        // 这三个字段必须保持 null，否则 MyBatis-Plus 会把它们写回库
        assertNull(patch.getStudentId());
        assertNull(patch.getAttendDate());
        assertNull(patch.getCourseId());
    }

    // ========== stats ==========

    @Test
    @DisplayName("出勤统计：课程不存在 → 404")
    void stats_courseNotFound() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.stats(COURSE_ID)).getCode());
    }

    @Test
    @DisplayName("出勤统计：正常返回 Mapper 结果")
    void stats_success() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(course());
        AttendanceStatVO vo = new AttendanceStatVO();
        vo.setStudentId(1L);
        when(attendanceMapper.selectStatsByCourse(COURSE_ID)).thenReturn(List.of(vo));

        assertEquals(1, service.stats(COURSE_ID).size());
    }
}

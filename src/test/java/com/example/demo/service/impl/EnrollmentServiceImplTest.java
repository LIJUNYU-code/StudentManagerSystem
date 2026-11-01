package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.EnrollmentQueryDTO;
import com.example.demo.dto.EnrollmentRequestDTO;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.mapper.ScoreMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.vo.EnrollmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EnrollmentServiceImpl 单元测试。
 * <p>
 * 选课模块的业务规则比学生模块更"有故事"：
 * 它要保证「一个学生不能重复选同一门课」，还要保证「有成绩的选课不能退」——
 * 后者是数据完整性的最后一道闸，一旦破了就会产生指向不存在选课的孤儿成绩。
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private ScoreMapper scoreMapper;

    private EnrollmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentServiceImpl(enrollmentMapper, studentMapper, courseMapper, scoreMapper);
    }

    private EnrollmentRequestDTO request(Long studentId, Long courseId) {
        EnrollmentRequestDTO dto = new EnrollmentRequestDTO();
        dto.setStudentId(studentId);
        dto.setCourseId(courseId);
        return dto;
    }

    private Student existingStudent() {
        Student s = new Student();
        s.setId(7L);
        s.setName("张三");
        return s;
    }

    private Course existingCourse() {
        Course c = new Course();
        c.setId(3L);
        c.setName("数据结构");
        return c;
    }

    // ========== add ==========

    @Test
    @DisplayName("选课：学生不存在 → 404，且不执行 insert")
    void add_studentNotFound() {
        when(studentMapper.selectById(7L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request(7L, 3L)));

        assertEquals(404, e.getCode());
        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("选课：课程不存在 → 404，且不执行 insert")
    void add_courseNotFound() {
        when(studentMapper.selectById(7L)).thenReturn(existingStudent());
        when(courseMapper.selectById(3L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request(7L, 3L)));

        assertEquals(404, e.getCode());
        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("选课：重复选同一门课 → 409，且不执行 insert")
    void add_duplicateEnrollment() {
        when(studentMapper.selectById(7L)).thenReturn(existingStudent());
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());
        when(enrollmentMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request(7L, 3L)));

        assertEquals(409, e.getCode());
        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("选课成功：返回的 VO 同时带学生名和课程名")
    void add_success() {
        when(studentMapper.selectById(7L)).thenReturn(existingStudent());
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.insert(any(Enrollment.class))).thenReturn(1);

        EnrollmentResponse resp = service.add(request(7L, 3L));

        assertEquals("张三", resp.getStudentName());
        assertEquals("数据结构", resp.getCourseName());
        assertEquals(7L, resp.getStudentId());
        assertEquals(3L, resp.getCourseId());

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getStudentId());
        assertEquals(3L, captor.getValue().getCourseId());
    }

    // ========== page ==========

    @Test
    @DisplayName("选课名单：不带 courseId → 400（不是 404，因为不是资源不存在）")
    void page_missingCourseId() {
        EnrollmentQueryDTO dto = new EnrollmentQueryDTO();
        dto.setCourseId(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.page(dto));

        assertEquals(400, e.getCode(), "缺参数应为 400；用 404 会让前端以为课程被删了");
        verify(courseMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("选课名单：课程不存在 → 404")
    void page_courseNotFound() {
        EnrollmentQueryDTO dto = new EnrollmentQueryDTO();
        dto.setCourseId(88L);
        when(courseMapper.selectById(88L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.page(dto));

        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("选课名单：没有选课记录 → 返回空页，且不查学生表")
    void page_emptyRecords() {
        EnrollmentQueryDTO dto = new EnrollmentQueryDTO();
        dto.setCourseId(3L);
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());

        Page<Enrollment> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(enrollmentMapper.selectPage(any(), any())).thenReturn(emptyPage);

        PageResult<EnrollmentResponse> result = service.page(dto);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        // 没有学生 id 可查时，不该白跑一趟 selectBatchIds
        verify(studentMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("选课名单：学生姓名批量补齐（一条 SQL 查回，不是循环里逐条查）")
    void page_fillsStudentNamesInOneBatch() {
        EnrollmentQueryDTO dto = new EnrollmentQueryDTO();
        dto.setCourseId(3L);
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());

        Enrollment e1 = new Enrollment();
        e1.setId(11L);
        e1.setStudentId(7L);
        e1.setCourseId(3L);
        Enrollment e2 = new Enrollment();
        e2.setId(12L);
        e2.setStudentId(8L);
        e2.setCourseId(3L);

        Page<Enrollment> page = new Page<>(1, 10);
        page.setRecords(List.of(e1, e2));
        page.setTotal(2);
        when(enrollmentMapper.selectPage(any(), any())).thenReturn(page);

        Student s1 = new Student();
        s1.setId(7L);
        s1.setName("张三");
        Student s2 = new Student();
        s2.setId(8L);
        s2.setName("李四");
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(s1, s2));

        PageResult<EnrollmentResponse> result = service.page(dto);

        assertEquals(2L, result.getTotal());
        assertEquals("张三", result.getRecords().get(0).getStudentName());
        assertEquals("李四", result.getRecords().get(1).getStudentName());
        assertEquals("数据结构", result.getRecords().get(0).getCourseName());
        // N+1 的防线：批量查询只应发生一次
        verify(studentMapper, times(1)).selectBatchIds(any());
    }

    // ========== delete ==========

    @Test
    @DisplayName("退课：选课记录不存在 → 404")
    void delete_notFound() {
        when(enrollmentMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(99L));

        assertEquals(404, e.getCode());
        verify(enrollmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("退课：该选课已有成绩 → 409，绝不删除（否则留下孤儿成绩）")
    void delete_hasScoreBlocked() {
        when(enrollmentMapper.selectById(11L)).thenReturn(new Enrollment());
        when(scoreMapper.selectCount(any())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(409, e.getCode());
        assertTrue(e.getMessage().contains("成绩"), "错误消息应说明原因");
        // 这是数据完整性的核心断言：有成绩时一行都不能删
        verify(enrollmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("退课成功：没有成绩 → 执行删除")
    void delete_success() {
        when(enrollmentMapper.selectById(11L)).thenReturn(new Enrollment());
        when(scoreMapper.selectCount(any())).thenReturn(0L);
        when(enrollmentMapper.deleteById(11L)).thenReturn(1);

        service.delete(11L);

        verify(enrollmentMapper).deleteById(11L);
    }
}

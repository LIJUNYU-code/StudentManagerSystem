package com.example.demo.service.impl;

import com.example.demo.common.PageResult;
import com.example.demo.dto.ScoreQueryDTO;
import com.example.demo.dto.ScoreRequestDTO;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.mapper.ScoreMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.vo.ScoreResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ScoreServiceImpl 单元测试。
 * <p>
 * 这里验证的是"成绩模块的业务规则"，不是 SQL 是否正确：
 * join 分页 SQL 的真伪要靠端到端测试（真实 MySQL）来兜，
 * 单元测试只保证"Service 拿到数据之后怎么判断、怎么组装"是对的。
 */
@ExtendWith(MockitoExtension.class)
class ScoreServiceImplTest {

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private CourseMapper courseMapper;

    private ScoreServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScoreServiceImpl(scoreMapper, enrollmentMapper, studentMapper, courseMapper);
    }

    @AfterEach
    void tearDown() {
        // query() 会读 SecurityContext，测试之间必须清理，否则会串味
        SecurityContextHolder.clearContext();
    }

    /** 把当前线程伪装成"id 为 userId 的已登录用户" */
    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private ScoreRequestDTO request(Long enrollmentId, String value, String examType) {
        ScoreRequestDTO dto = new ScoreRequestDTO();
        dto.setEnrollmentId(enrollmentId);
        dto.setValue(new BigDecimal(value));
        dto.setExamType(examType);
        return dto;
    }

    // ========== add ==========

    @Test
    @DisplayName("录成绩：选课记录不存在 → 404，且不执行 insert")
    void add_enrollmentNotFound() {
        when(enrollmentMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request(99L, "85", "期中")));

        assertEquals(404, e.getCode());
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("录成绩：同一次选课的同一考试类型已存在 → 409，且不执行 insert")
    void add_duplicateExamType() {
        when(enrollmentMapper.selectById(5L)).thenReturn(new Enrollment());
        when(scoreMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request(5L, "85", "期中")));

        assertEquals(409, e.getCode());
        // 错误消息要把考试类型带出来，否则前端不知道该改哪一条
        assertTrue(e.getMessage().contains("期中"), "错误消息应包含考试类型");
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("录成绩并发漏网：insert 抛 DuplicateKeyException → 翻译成 409 而不是 500")
    void add_duplicateKeyTranslated() {
        when(enrollmentMapper.selectById(5L)).thenReturn(new Enrollment());
        when(scoreMapper.selectCount(any())).thenReturn(0L);
        when(scoreMapper.insert(any(Score.class)))
                .thenThrow(new DuplicateKeyException("uk_score_enrollment_exam"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request(5L, "85", "期末")));

        assertEquals(409, e.getCode());
    }

    @Test
    @DisplayName("录成绩成功：字段被完整复制到实体，insert 被调用一次")
    void add_success() {
        when(enrollmentMapper.selectById(5L)).thenReturn(new Enrollment());
        when(scoreMapper.selectCount(any())).thenReturn(0L);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        service.add(request(5L, "85.5", "期中"));

        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(captor.capture());
        Score saved = captor.getValue();
        assertEquals(5L, saved.getEnrollmentId());
        assertEquals("期中", saved.getExamType());
        // BigDecimal 用 compareTo 比数值，避免 85.5 与 85.50 因 scale 不同误判
        assertEquals(0, new BigDecimal("85.5").compareTo(saved.getValue()));
    }

    // ========== page ==========

    @Test
    @DisplayName("课程成绩单：课程不存在 → 404，且不查任何成绩")
    void page_courseNotFound() {
        ScoreQueryDTO dto = new ScoreQueryDTO();
        dto.setCourseId(88L);
        when(courseMapper.selectById(88L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.page(dto));

        assertEquals(404, e.getCode());
        verify(scoreMapper, never()).countByCourse(anyLong());
        verify(scoreMapper, never()).selectPageByCourse(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("课程成绩单：总数为 0 → 直接返回空页，不再发起第二趟查询")
    void page_emptyShortCircuits() {
        ScoreQueryDTO dto = new ScoreQueryDTO();
        dto.setCourseId(1L);
        when(courseMapper.selectById(1L)).thenReturn(new Course());
        when(scoreMapper.countByCourse(1L)).thenReturn(0L);

        PageResult<ScoreResponse> result = service.page(dto);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        // 关键：count 为 0 时不该再跑一趟 selectPageByCourse
        verify(scoreMapper, never()).selectPageByCourse(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("课程成绩单：offset 按 (页码-1)×每页条数 计算，页码 2 时应为 10")
    void page_offsetComputedFromPageNumber() {
        ScoreQueryDTO dto = new ScoreQueryDTO();
        dto.setCourseId(1L);
        dto.setPage(2);
        dto.setPageSize(10);
        when(courseMapper.selectById(1L)).thenReturn(new Course());
        when(scoreMapper.countByCourse(1L)).thenReturn(15L);
        when(scoreMapper.selectPageByCourse(1L, 10L, 10L)).thenReturn(List.of());

        PageResult<ScoreResponse> result = service.page(dto);

        assertEquals(15L, result.getTotal());
        // 精确校验 offset：这里算错会导致翻页重复/丢数据，是最容易出 bug 的算术
        verify(scoreMapper).selectPageByCourse(1L, 10L, 10L);
    }

    // ========== query（学生自助查分） ==========

    @Test
    @DisplayName("我的成绩：学生没有任何选课 → 返回空列表，且不查成绩表")
    void query_noEnrollment() {
        loginAs(7L);
        when(studentMapper.selectById(7L)).thenReturn(new Student());
        when(enrollmentMapper.selectList(any())).thenReturn(List.of());

        List<ScoreResponse> result = service.query();

        assertTrue(result.isEmpty());
        verify(scoreMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("我的成绩：有选课但没录成绩 → 返回空列表")
    void query_noScore() {
        loginAs(7L);
        when(studentMapper.selectById(7L)).thenReturn(new Student());
        Enrollment e = new Enrollment();
        e.setId(11L);
        e.setCourseId(3L);
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(e));
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());
        when(scoreMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.query().isEmpty());
    }

    @Test
    @DisplayName("我的成绩：选课指向的课程已查不到（课程被逻辑删除/脏数据）→ 课程名为 null，但不崩")
    void query_missingCourseDoesNotThrow() {
        loginAs(7L);

        Student student = new Student();
        student.setId(7L);
        student.setName("张三");
        when(studentMapper.selectById(7L)).thenReturn(student);

        Enrollment e = new Enrollment();
        e.setId(11L);
        e.setCourseId(3L);
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(e));

        // 课程查不回来（例如课程被逻辑删除，selectBatchIds 会按 @TableLogic 过滤掉）
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of());

        Score score = new Score();
        score.setId(100L);
        score.setEnrollmentId(11L);
        score.setValue(new BigDecimal("92"));
        score.setExamType("期末");
        when(scoreMapper.selectList(any())).thenReturn(List.of(score));

        // 这条曾经会抛 NullPointerException（Collectors.toMap 不允许 null value），
        // 导致学生一打开"我的成绩"就 500。修复后应降级为"课程名为空"，页面照常显示分数。
        List<ScoreResponse> result = assertDoesNotThrow(() -> service.query());

        assertEquals(1, result.size());
        assertNull(result.get(0).getCourseName());
        assertEquals(0, new BigDecimal("92").compareTo(result.get(0).getValue()));
    }

    @Test
    @DisplayName("我的成绩：每条成绩都补上课程名（学生端要显示是哪门课）")
    void query_fillsCourseName() {
        loginAs(7L);

        Student student = new Student();
        student.setId(7L);
        student.setName("张三");
        student.setStudentNo("S202500001");
        when(studentMapper.selectById(7L)).thenReturn(student);

        Enrollment e = new Enrollment();
        e.setId(11L);
        e.setCourseId(3L);
        when(enrollmentMapper.selectList(any())).thenReturn(List.of(e));

        Course course = new Course();
        course.setId(3L);
        course.setName("数据结构");
        when(courseMapper.selectBatchIds(any())).thenReturn(List.of(course));

        Score score = new Score();
        score.setId(100L);
        score.setEnrollmentId(11L);
        score.setValue(new BigDecimal("92"));
        score.setExamType("期末");
        when(scoreMapper.selectList(any())).thenReturn(List.of(score));

        List<ScoreResponse> result = service.query();

        assertEquals(1, result.size());
        ScoreResponse vo = result.get(0);
        assertEquals("数据结构", vo.getCourseName(), "课程名必须被补上");
        assertEquals("张三", vo.getStudentName());
        assertEquals("S202500001", vo.getStudentNo());
        assertEquals("期末", vo.getExamType());
        // 学生端自查不应暴露任何他人信息：这里断言只返回本人那一条
        assertEquals(11L, vo.getEnrollmentId());
    }
}

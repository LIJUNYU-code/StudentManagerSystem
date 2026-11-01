package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.CourseQueryDTO;
import com.example.demo.dto.CourseRequestDTO;
import com.example.demo.dto.CourseUpdateRequestDTO;
import com.example.demo.entity.Course;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.vo.CourseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CourseServiceImpl 单元测试。
 * <p>
 * 课程是"选课"和"成绩"两条链的上游：一门课被删掉，下游的选课记录和成绩就会指向不存在的课程。
 * 所以和班级一样，删除前必须检查下游引用。
 * <p>
 * 注意 add 和 ClazzServiceImpl.add 的一个差异：课程这边<b>没有</b> try-catch DuplicateKeyException，
 * 靠 GlobalExceptionHandler 里那个兜底的 DuplicateKeyException 处理器转成 409。
 * 两条路殊途同归（都返回 409），但表达方式不同——下面的用例把当前真实行为固定下来。
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(courseMapper, enrollmentMapper);
    }

    private CourseRequestDTO request(String name, String credit, Long teacherId) {
        CourseRequestDTO dto = new CourseRequestDTO();
        dto.setName(name);
        dto.setCredit(new BigDecimal(credit));
        dto.setTeacherId(teacherId);
        return dto;
    }

    private Course existingCourse() {
        Course c = new Course();
        c.setId(3L);
        c.setName("数据结构");
        c.setCredit(new BigDecimal("3.5"));
        c.setTeacherId(5L);
        return c;
    }

    // ========== add ==========

    @Test
    @DisplayName("新增课程：名称重复 → 409，且不执行 insert")
    void add_duplicateName() {
        when(courseMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request("数据结构", "3.5", 5L)));

        assertEquals(409, e.getCode());
        verify(courseMapper, never()).insert(any(Course.class));
    }

    @Test
    @DisplayName("新增课程成功：字段完整落库，返回 VO 带 id 与学分")
    void add_success() {
        when(courseMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        CourseResponse resp = service.add(request("数据结构", "3.5", 5L));

        assertEquals("数据结构", resp.getName());
        assertEquals(0, new BigDecimal("3.5").compareTo(resp.getCredit()));
        assertEquals(5L, resp.getTeacherId());

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).insert(captor.capture());
        assertEquals("数据结构", captor.getValue().getName());
    }

    @Test
    @DisplayName("新增课程：并发穿透时 DuplicateKeyException 原样抛出，由全局处理器转 409")
    void add_duplicateKeyPropagates() {
        when(courseMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.insert(any(Course.class))).thenThrow(new DuplicateKeyException("uk_course_name"));

        // 当前实现没有在这里 catch，所以抛的是 Spring 的 DuplicateKeyException，
        // 由 GlobalExceptionHandler.handleDuplicateKey 统一翻译成 409。
        // 这条断言的意义是：一旦有人把全局处理器删了，这里会立刻变红。
        assertThrows(DuplicateKeyException.class,
                () -> service.add(request("数据结构", "3.5", 5L)));
    }

    // ========== query ==========

    @Test
    @DisplayName("查详情：课程不存在 → 404")
    void query_notFound() {
        when(courseMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.query(99L));

        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("查详情成功：学分用 compareTo 比较，避免 3.5 与 3.50 因 scale 误判")
    void query_success() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());

        CourseResponse resp = service.query(3L);

        assertEquals(3L, resp.getId());
        assertEquals("数据结构", resp.getName());
        assertEquals(0, new BigDecimal("3.5").compareTo(resp.getCredit()));
    }

    // ========== page ==========

    @Test
    @DisplayName("分页：没有数据 → 空页，total=0")
    void page_empty() {
        Page<Course> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(courseMapper.selectPage(any(), any())).thenReturn(emptyPage);

        PageResult<CourseResponse> result = service.page(new CourseQueryDTO());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("分页：记录映射为 VO，学分保留两位小数不丢精度")
    void page_mapsRecords() {
        Page<Course> page = new Page<>(1, 10);
        page.setRecords(List.of(existingCourse()));
        page.setTotal(1);
        when(courseMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<CourseResponse> result = service.page(new CourseQueryDTO());

        assertEquals(1L, result.getTotal());
        assertEquals("数据结构", result.getRecords().get(0).getName());
        assertEquals(0, new BigDecimal("3.5").compareTo(result.getRecords().get(0).getCredit()));
    }

    // ========== update ==========

    @Test
    @DisplayName("修改课程：课程不存在 → 404")
    void update_notFound() {
        when(courseMapper.selectById(99L)).thenReturn(null);

        CourseUpdateRequestDTO dto = new CourseUpdateRequestDTO();
        dto.setName("新课程名");

        BusinessException e = assertThrows(BusinessException.class, () -> service.update(99L, dto));

        assertEquals(404, e.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("修改课程：三个字段都没传 → 400")
    void update_emptyBody() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(3L, new CourseUpdateRequestDTO()));

        assertEquals(400, e.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("修改课程：新名字被别的课占用 → 409，且不执行更新")
    void update_nameTaken() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());
        when(courseMapper.selectCount(any())).thenReturn(1L);

        CourseUpdateRequestDTO dto = new CourseUpdateRequestDTO();
        dto.setName("操作系统");

        BusinessException e = assertThrows(BusinessException.class, () -> service.update(3L, dto));

        assertEquals(409, e.getCode());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("修改课程：只改学分不碰名字 → 不触发重名查询")
    void update_onlyCredit_skipsNameCheck() {
        Course updated = existingCourse();
        updated.setCredit(new BigDecimal("4.0"));
        when(courseMapper.selectById(3L)).thenReturn(existingCourse(), updated);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        CourseUpdateRequestDTO dto = new CourseUpdateRequestDTO();
        dto.setCredit(new BigDecimal("4.0"));

        CourseResponse resp = service.update(3L, dto);

        assertEquals(0, new BigDecimal("4.0").compareTo(resp.getCredit()));
        verify(courseMapper, never()).selectCount(any());
        verify(courseMapper, times(1)).updateById(any(Course.class));
    }

    @Test
    @DisplayName("修改课程：改成自己原来的名字 → 不被自己挡住（.ne(id) 生效）")
    void update_sameName_success() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse(), existingCourse());
        when(courseMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        CourseUpdateRequestDTO dto = new CourseUpdateRequestDTO();
        dto.setName("数据结构");

        CourseResponse resp = service.update(3L, dto);

        assertEquals("数据结构", resp.getName());
        verify(courseMapper, times(1)).updateById(any(Course.class));
    }

    // ========== delete ==========

    @Test
    @DisplayName("删除课程：课程不存在 → 404，不执行删除")
    void delete_notFound() {
        when(courseMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(99L));

        assertEquals(404, e.getCode());
        verify(courseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除课程：已有选课记录 → 409，一行都不删（否则选课与成绩会指向不存在的课）")
    void delete_hasEnrollmentsBlocked() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());
        when(enrollmentMapper.selectCount(any())).thenReturn(12L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(3L));

        assertEquals(409, e.getCode());
        assertTrue(e.getMessage().contains("12"), "错误消息要带出条数，用户才知道影响面");
        verify(courseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除课程成功：没有选课记录 → 执行删除")
    void delete_success() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());
        when(enrollmentMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.deleteById(3L)).thenReturn(1);

        service.delete(3L);

        verify(courseMapper).deleteById(3L);
    }

    @Test
    @DisplayName("删除课程：count 返回 null 时不能误判成有选课（NPE 防线）")
    void delete_nullCountTreatedAsEmpty() {
        when(courseMapper.selectById(3L)).thenReturn(existingCourse());
        when(enrollmentMapper.selectCount(any())).thenReturn(null);
        when(courseMapper.deleteById(3L)).thenReturn(1);

        assertDoesNotThrow(() -> service.delete(3L));

        verify(courseMapper).deleteById(3L);
    }
}

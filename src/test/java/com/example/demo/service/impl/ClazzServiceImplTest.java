package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.ClazzQueryDTO;
import com.example.demo.dto.ClazzRequestDTO;
import com.example.demo.dto.ClazzUpdateRequestDTO;
import com.example.demo.entity.Clazz;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.ClazzMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.vo.ClazzResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ClazzServiceImpl 单元测试。
 * <p>
 * 班级模块逻辑本身不复杂（增删改查 + 重名校验），但有一条规则值得反复锁住：
 * <b>班级下还挂着学生时不许删</b>。如果这条防线破了，删班不会报错，
 * 但那些学生会变成 clazz_id 指向不存在班级的孤儿数据，而且界面上看不出来——
 * 属于"删的时候没感觉、用的时候才发现"的典型数据事故。
 * <p>
 * 因此这里的断言重点不是"删除成功返回 200"，而是"删除被拦住时一行都没删"。
 */
@ExtendWith(MockitoExtension.class)
class ClazzServiceImplTest {

    @Mock
    private ClazzMapper clazzMapper;

    @Mock
    private StudentMapper studentMapper;

    private ClazzServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClazzServiceImpl(clazzMapper, studentMapper);
    }

    private ClazzRequestDTO request(String name, String grade, Integer teacherId) {
        ClazzRequestDTO dto = new ClazzRequestDTO();
        dto.setName(name);
        dto.setGrade(grade);
        dto.setTeacherId(teacherId);
        return dto;
    }

    private Clazz existingClazz() {
        Clazz c = new Clazz();
        c.setId(1L);
        c.setName("软件工程一班");
        c.setGrade("2023");
        c.setTeacherId(5);
        return c;
    }

    // ========== add ==========

    @Test
    @DisplayName("新增班级：名称重复 → 409，且不执行 insert")
    void add_duplicateName() {
        when(clazzMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request("软件工程一班", "2023", 5)));

        assertEquals(409, e.getCode());
        verify(clazzMapper, never()).insert(any(Clazz.class));
    }

    @Test
    @DisplayName("新增班级成功：字段完整落到 insert 的对象上")
    void add_success() {
        when(clazzMapper.selectCount(any())).thenReturn(0L);
        when(clazzMapper.insert(any(Clazz.class))).thenReturn(1);

        service.add(request("软件工程一班", "2023", 5));

        ArgumentCaptor<Clazz> captor = ArgumentCaptor.forClass(Clazz.class);
        verify(clazzMapper).insert(captor.capture());
        assertEquals("软件工程一班", captor.getValue().getName());
        assertEquals("2023", captor.getValue().getGrade());
        assertEquals(5, captor.getValue().getTeacherId());
    }

    @Test
    @DisplayName("新增班级：并发穿透时 DuplicateKeyException → 409，不是 500")
    void add_duplicateKeyTranslated() {
        // 先查重通过（说明查的那一刻确实没重名），但插入时被唯一索引拦下
        when(clazzMapper.selectCount(any())).thenReturn(0L);
        when(clazzMapper.insert(any(Clazz.class))).thenThrow(new DuplicateKeyException("uk_clazz_name"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.add(request("软件工程一班", "2023", 5)));

        assertEquals(409, e.getCode(), "查重与插入之间存在竞态，必须靠唯一索引兜底并翻译成 409");
    }

    // ========== query ==========

    @Test
    @DisplayName("查详情：班级不存在 → 404，不是返回 null")
    void query_notFound() {
        when(clazzMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.query(99L));

        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("查详情成功：字段完整映射到 VO")
    void query_success() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz());

        ClazzResponse resp = service.query(1L);

        assertEquals(1L, resp.getId());
        assertEquals("软件工程一班", resp.getName());
        assertEquals("2023", resp.getGrade());
        assertEquals(5, resp.getTeacherId());
    }

    // ========== page ==========

    @Test
    @DisplayName("分页：没有数据 → 空页，total=0，不报错")
    void page_empty() {
        Page<Clazz> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(clazzMapper.selectPage(any(), any())).thenReturn(emptyPage);

        PageResult<ClazzResponse> result = service.page(new ClazzQueryDTO());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("分页：记录逐条映射为 VO，不泄露实体字段")
    void page_mapsRecords() {
        Page<Clazz> page = new Page<>(1, 10);
        page.setRecords(List.of(existingClazz()));
        page.setTotal(1);
        when(clazzMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<ClazzResponse> result = service.page(new ClazzQueryDTO());

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("软件工程一班", result.getRecords().get(0).getName());
    }

    // ========== update ==========

    @Test
    @DisplayName("修改班级：班级不存在 → 404")
    void update_notFound() {
        when(clazzMapper.selectById(99L)).thenReturn(null);

        ClazzUpdateRequestDTO dto = new ClazzUpdateRequestDTO();
        dto.setName("新名字");

        BusinessException e = assertThrows(BusinessException.class, () -> service.update(99L, dto));

        assertEquals(404, e.getCode());
        verify(clazzMapper, never()).updateById(any(Clazz.class));
    }

    @Test
    @DisplayName("修改班级：三个字段都没传 → 400（空更新要挡住，否则等于白跑一趟）")
    void update_emptyBody() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(1L, new ClazzUpdateRequestDTO()));

        assertEquals(400, e.getCode());
        verify(clazzMapper, never()).updateById(any(Clazz.class));
    }

    @Test
    @DisplayName("修改班级：新名字被别的班占用 → 409，且不执行更新")
    void update_nameTaken() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz());
        when(clazzMapper.selectCount(any())).thenReturn(1L);

        ClazzUpdateRequestDTO dto = new ClazzUpdateRequestDTO();
        dto.setName("软件工程二班");

        BusinessException e = assertThrows(BusinessException.class, () -> service.update(1L, dto));

        assertEquals(409, e.getCode());
        verify(clazzMapper, never()).updateById(any(Clazz.class));
    }

    @Test
    @DisplayName("修改班级：只改年级不碰名字 → 不触发重名查询，返回更新后的完整数据")
    void update_onlyGrade_skipsNameCheck() {
        Clazz updated = existingClazz();
        updated.setGrade("2024");
        // 第一次查用于存在性校验，第二次查用于返回最新数据
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz(), updated);
        when(clazzMapper.updateById(any(Clazz.class))).thenReturn(1);

        ClazzUpdateRequestDTO dto = new ClazzUpdateRequestDTO();
        dto.setGrade("2024");

        ClazzResponse resp = service.update(1L, dto);

        assertEquals("2024", resp.getGrade());
        // 名字没改就不该去查重，省一次无意义的 SQL
        verify(clazzMapper, never()).selectCount(any());
        verify(clazzMapper, times(1)).updateById(any(Clazz.class));
    }

    @Test
    @DisplayName("修改班级：改名字时重名查询必须排除自己（.ne(id)），否则改回原名会被自己挡住")
    void update_sameName_success() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz(), existingClazz());
        when(clazzMapper.selectCount(any())).thenReturn(0L);
        when(clazzMapper.updateById(any(Clazz.class))).thenReturn(1);

        ClazzUpdateRequestDTO dto = new ClazzUpdateRequestDTO();
        dto.setName("软件工程一班");

        ClazzResponse resp = service.update(1L, dto);

        assertEquals("软件工程一班", resp.getName());
        verify(clazzMapper, times(1)).updateById(any(Clazz.class));
    }

    // ========== delete ==========

    @Test
    @DisplayName("删除班级：班级不存在 → 404，不执行删除")
    void delete_notFound() {
        when(clazzMapper.selectById(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(99L));

        assertEquals(404, e.getCode());
        verify(clazzMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除班级：班里还有学生 → 409，一行都不删（核心数据完整性断言）")
    void delete_hasStudentsBlocked() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz());
        when(studentMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals(409, e.getCode());
        assertTrue(e.getMessage().contains("3"), "错误消息要告诉用户还有几名学生，否则不知道从哪下手");
        // 这是本测试的核心：拦住了就必须真的没删，不能先删后报错
        verify(clazzMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除班级成功：班里没有学生 → 执行删除")
    void delete_success() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz());
        when(studentMapper.selectCount(any())).thenReturn(0L);
        when(clazzMapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(clazzMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除班级：count 返回 null 时不能误判成有学生（NPE 防线）")
    void delete_nullCountTreatedAsEmpty() {
        when(clazzMapper.selectById(1L)).thenReturn(existingClazz());
        when(studentMapper.selectCount(any())).thenReturn(null);
        when(clazzMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> service.delete(1L));

        verify(clazzMapper).deleteById(1L);
    }
}

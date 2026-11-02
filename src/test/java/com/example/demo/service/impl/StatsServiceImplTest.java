package com.example.demo.service.impl;

import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.ClazzMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.StatsMapper;
import com.example.demo.service.impl.StatsServiceImpl;
import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * StatsServiceImpl 单元测试。
 * <p>
 * 统计模块最容易出的不是"算错"，而是<b>返回 null 或段数不齐</b>，
 * 让前端图表直接画不出来。所以这里的断言重点是：
 * <ul>
 *   <li>空课程（一条成绩都没有）返回 0 而不是 null —— 否则前端 ECharts 会崩</li>
 *   <li>分数段永远 5 段 —— 否则柱状图会缺柱子，看起来像数据错乱</li>
 *   <li>缺失的段补 0，且顺序固定 —— 顺序错了图表的横轴就乱</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    @Mock
    private StatsMapper statsMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private ClazzMapper clazzMapper;

    private StatsServiceImpl service;

    private static final Long COURSE_ID = 10L;
    private static final Long CLAZZ_ID = 3L;

    @BeforeEach
    void setUp() {
        service = new StatsServiceImpl(statsMapper, courseMapper, clazzMapper);
    }

    private void courseExists() {
        com.example.demo.entity.Course c = new com.example.demo.entity.Course();
        c.setId(COURSE_ID);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(c);
    }

    // ========== courseSummary ==========

    @Test
    @DisplayName("课程概览：课程不存在 → 404，不查聚合")
    void summary_courseNotFound() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.courseSummary(COURSE_ID));

        assertEquals(404, e.getCode());
        verify(statsMapper, never()).selectCourseSummary(anyLong());
    }

    @Test
    @DisplayName("课程概览：Mapper 返回 null 时兜底成全 0（前端不能收到 null）")
    void summary_nullFallback() {
        courseExists();
        when(statsMapper.selectCourseSummary(COURSE_ID)).thenReturn(null);

        CourseSummaryVO vo = service.courseSummary(COURSE_ID);

        assertNotNull(vo);
        assertEquals(0L, vo.getCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getAvg()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getMax()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getMin()));
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getPassRate()));
    }

    @Test
    @DisplayName("课程概览：正常数据原样返回")
    void summary_success() {
        courseExists();
        CourseSummaryVO raw = CourseSummaryVO.builder()
                .count(43L)
                .avg(new BigDecimal("71.5"))
                .max(new BigDecimal("100.0"))
                .min(new BigDecimal("41.0"))
                .passRate(new BigDecimal("0.6744"))
                .build();
        when(statsMapper.selectCourseSummary(COURSE_ID)).thenReturn(raw);

        CourseSummaryVO vo = service.courseSummary(COURSE_ID);

        assertEquals(43L, vo.getCount());
        assertEquals(new BigDecimal("71.5"), vo.getAvg());
        assertEquals(new BigDecimal("0.6744"), vo.getPassRate());
    }

    // ========== courseDistribution ==========

    @Test
    @DisplayName("分数段：一条数据都没有 → 仍然返回固定 5 段，全部为 0")
    void distribution_emptyStillFiveBuckets() {
        courseExists();
        when(statsMapper.selectDistributionByCourse(COURSE_ID)).thenReturn(List.of());

        List<ScoreBucketVO> result = service.courseDistribution(COURSE_ID);

        assertEquals(5, result.size());
        assertEquals(List.of("0-59", "60-69", "70-79", "80-89", "90-100"),
                result.stream().map(ScoreBucketVO::getBucket).toList());
        assertTrue(result.stream().allMatch(b -> b.getCount() == 0L));
    }

    @Test
    @DisplayName("分数段：只有部分段有数据 → 缺失段补 0 且顺序固定（不按返回顺序）")
    void distribution_fillMissing() {
        courseExists();
        // 数据库只返回两段，且故意打乱顺序（真实 GROUP BY 的顺序不可依赖）
        when(statsMapper.selectDistributionByCourse(COURSE_ID)).thenReturn(List.of(
                new ScoreBucketVO("90-100", 10L),
                new ScoreBucketVO("0-59", 14L)));

        List<ScoreBucketVO> result = service.courseDistribution(COURSE_ID);

        assertEquals(5, result.size());
        // 顺序必须是固定的 BUCKET_ORDER，而不是数据库返回的顺序
        assertEquals("0-59", result.get(0).getBucket());
        assertEquals(14L, result.get(0).getCount());
        assertEquals("60-69", result.get(1).getBucket());
        assertEquals(0L, result.get(1).getCount());
        assertEquals("90-100", result.get(4).getBucket());
        assertEquals(10L, result.get(4).getCount());
    }

    @Test
    @DisplayName("分数段：课程不存在 → 404")
    void distribution_courseNotFound() {
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.courseDistribution(COURSE_ID)).getCode());
    }

    // ========== clazzCompare ==========

    @Test
    @DisplayName("班级对比：clazzId 为 null → 400（不是 404，客户端没说清要查什么）")
    void compare_nullId() {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.clazzCompare(null)).getCode());
        verify(statsMapper, never()).selectCourseAvgByClazz(anyLong());
    }

    @Test
    @DisplayName("班级对比：班级不存在 → 404")
    void compare_clazzNotFound() {
        when(clazzMapper.selectById(CLAZZ_ID)).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.clazzCompare(CLAZZ_ID)).getCode());
    }

    @Test
    @DisplayName("班级对比：正常返回各课平均分")
    void compare_success() {
        com.example.demo.entity.Clazz c = new com.example.demo.entity.Clazz();
        c.setId(CLAZZ_ID);
        when(clazzMapper.selectById(CLAZZ_ID)).thenReturn(c);
        when(statsMapper.selectCourseAvgByClazz(CLAZZ_ID)).thenReturn(List.of(
                CourseAvgVO.builder().courseId(1L).courseName("数据结构").avg(new BigDecimal("70.1")).count(20L).build()));

        List<CourseAvgVO> result = service.clazzCompare(CLAZZ_ID);

        assertEquals(1, result.size());
        assertEquals("数据结构", result.get(0).getCourseName());
    }
}

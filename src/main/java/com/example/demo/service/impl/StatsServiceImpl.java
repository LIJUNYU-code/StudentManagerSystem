package com.example.demo.service.impl;

import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.ClazzMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.StatsMapper;
import com.example.demo.service.StatsService;
import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import com.example.demo.vo.StudentTrendVO;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class StatsServiceImpl implements StatsService {

    /**
     * 分数段的固定顺序。
     * <p>
     * 为什么在 Java 里写死而不是完全信任 SQL 的 GROUP BY：
     * GROUP BY 只返回「有数据的段」。如果这个课程所有人都及格，0-59 那段就不存在，
     * 前端柱状图会少一根柱子——看起来像数据错乱，实际是"0 条"。
     * 用固定列表补 0，保证图表形状永远稳定。这是「接口契约要稳定」的具体体现。
     */
    private static final List<String> BUCKET_ORDER =
            List.of("0-59", "60-69", "70-79", "80-89", "90-100");

    private final StatsMapper statsMapper;
    private final CourseMapper courseMapper;
    private final ClazzMapper clazzMapper;

    @Override
    public CourseSummaryVO courseSummary(Long courseId) {
        requireCourse(courseId);
        CourseSummaryVO summary = statsMapper.selectCourseSummary(courseId);
        // 双保险：SQL 里已经 IFNULL 兜过，这里再挡一次 null。
        // 理由：如果将来有人改了 SQL 忘了 IFNULL，接口仍然不会返回 null 让前端崩。
        if (summary == null) {
            return CourseSummaryVO.builder()
                    .count(0L)
                    .avg(BigDecimal.ZERO)
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.ZERO)
                    .passRate(BigDecimal.ZERO)
                    .build();
        }
        return summary;
    }

    @Override
    public List<ScoreBucketVO> courseDistribution(Long courseId) {
        requireCourse(courseId);
        List<ScoreBucketVO> raw = statsMapper.selectDistributionByCourse(courseId);

        // 把 SQL 返回的「有数据的段」映射成 Map，再按固定顺序补齐缺失的段
        Map<String, Long> countByBucket = new LinkedHashMap<>();
        for (ScoreBucketVO item : raw) {
            countByBucket.put(item.getBucket(), item.getCount());
        }

        List<ScoreBucketVO> result = new ArrayList<>(BUCKET_ORDER.size());
        for (String bucket : BUCKET_ORDER) {
            result.add(new ScoreBucketVO(bucket, countByBucket.getOrDefault(bucket, 0L)));
        }
        return result;
    }

    @Override
    public List<CourseAvgVO> clazzCompare(Long clazzId) {
        if (clazzId == null) {
            throw new BusinessException(400, "班级 id 不能为空");
        }
        if (clazzMapper.selectById(clazzId) == null) {
            throw new BusinessException(404, "班级不存在");
        }
        return statsMapper.selectCourseAvgByClazz(clazzId);
    }

    @Override
    public List<StudentTrendVO> myTrend() {
        Long currentId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return statsMapper.selectStudentTrend(currentId);
    }

    /** 课程不存在直接 404，省掉后面所有聚合查询。 */
    private void requireCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(400, "课程 id 不能为空");
        }
        if (courseMapper.selectById(courseId) == null) {
            throw new BusinessException(404, "课程不存在");
        }
    }
}

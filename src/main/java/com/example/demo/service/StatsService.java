package com.example.demo.service;

import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import com.example.demo.vo.StudentTrendVO;

import java.util.List;

/**
 * 成绩统计分析。
 * <p>
 * 这一层几乎只做三件事：权限/存在性校验、空值兜底、补齐空分数段。
 * 真正的聚合计算全部在 SQL 里（见 {@link com.example.demo.mapper.StatsMapper}）。
 */
public interface StatsService {

    /** 课程成绩概览（平均/最高/最低/及格率）。空课程返回全 0，不返回 null。 */
    CourseSummaryVO courseSummary(Long courseId);

    /** 分数段分布。固定返回 5 个段（0-59 / 60-69 / 70-79 / 80-89 / 90-100），空段补 0。 */
    List<ScoreBucketVO> courseDistribution(Long courseId);

    /** 班级各课程平均分对比。 */
    List<CourseAvgVO> clazzCompare(Long clazzId);

    /** 当前登录学生的成绩趋势（身份从 token 取，不接受参数）。 */
    List<StudentTrendVO> myTrend();
}

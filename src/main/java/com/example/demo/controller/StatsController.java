package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.service.StatsService;
import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import com.example.demo.vo.StudentTrendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成绩统计分析接口。
 *
 * <h3>权限设计</h3>
 * 前三个（课程概览/分布/班级对比）只给教师和管理员——它们是"看别人成绩"的视角。
 * 最后一个 /my/trend 只给学生，且不接受任何 id 参数，身份完全来自 token。
 * 这与已有的 GET /scores/my 是同一套思路：教师没有"自己的成绩趋势"，放开只会返回空列表。
 */
@Tag(name = "成绩统计", description = "课程成绩概览、分数段分布、班级课程对比，以及学生个人成绩趋势。数据供前端 ECharts 图表使用")
@RestController
@AllArgsConstructor
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "课程成绩概览", description = "返回该课程的成绩条数、平均分、最高分、最低分、及格率。无成绩时各项返回 0 而不是 null")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/course/{courseId}/summary")
    public Result<CourseSummaryVO> courseSummary(@PathVariable Long courseId) {
        return Result.success(statsService.courseSummary(courseId));
    }

    @Operation(summary = "课程分数段分布", description = "固定返回 5 段（0-59 / 60-69 / 70-79 / 80-89 / 90-100），没有成绩的段补 0，方便直接喂给柱状图")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/course/{courseId}/distribution")
    public Result<List<ScoreBucketVO>> courseDistribution(@PathVariable Long courseId) {
        return Result.success(statsService.courseDistribution(courseId));
    }

    @Operation(summary = "班级课程对比", description = "返回该班级各门课程的平均分与人数，用于班级内部横向对比")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/clazz/{clazzId}/compare")
    public Result<List<CourseAvgVO>> clazzCompare(@PathVariable Long clazzId) {
        return Result.success(statsService.clazzCompare(clazzId));
    }

    @Operation(summary = "我的成绩趋势", description = "仅学生可用，不接受任何 id 参数——身份来自 token")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/trend")
    public Result<List<StudentTrendVO>> myTrend() {
        return Result.success(statsService.myTrend());
    }
}

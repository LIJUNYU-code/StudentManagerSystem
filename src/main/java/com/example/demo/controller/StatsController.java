package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.service.StatsService;
import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import com.example.demo.vo.StudentTrendVO;
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
@RestController
@AllArgsConstructor
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/course/{courseId}/summary")
    public Result<CourseSummaryVO> courseSummary(@PathVariable Long courseId) {
        return Result.success(statsService.courseSummary(courseId));
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/course/{courseId}/distribution")
    public Result<List<ScoreBucketVO>> courseDistribution(@PathVariable Long courseId) {
        return Result.success(statsService.courseDistribution(courseId));
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/clazz/{clazzId}/compare")
    public Result<List<CourseAvgVO>> clazzCompare(@PathVariable Long clazzId) {
        return Result.success(statsService.clazzCompare(clazzId));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/trend")
    public Result<List<StudentTrendVO>> myTrend() {
        return Result.success(statsService.myTrend());
    }
}

package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.AttendanceBatchRequestDTO;
import com.example.demo.dto.AttendanceQueryDTO;
import com.example.demo.dto.AttendanceUpdateDTO;
import com.example.demo.service.AttendanceService;
import com.example.demo.vo.AttendanceRecordVO;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考勤管理。
 *
 * <h3>权限设计</h3>
 * 前四个（批量点名/名单/修改/统计）是教师视角，只给 TEACHER/ADMIN；
 * /my 只给学生，且不接受任何 id 参数——身份完全来自 token。
 * 与 /scores/my、/stats/my/trend 同一套思路。
 */
@Tag(name = "考勤管理", description = "按课程按日期批量点名、查看点名名单、修改单条记录、出勤率统计；学生可查自己的考勤明细")
@RestController
@AllArgsConstructor
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    /** 批量点名：覆盖语义，重复提交不会报 409。 */
    @Operation(summary = "批量点名", description = "覆盖语义——同一天同一节次重复提交会更新而非报 409。未选修该课程的学生返回 400")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/batch")
    public Result<Integer> batch(@Valid @RequestBody AttendanceBatchRequestDTO dto) {
        return Result.success(attendanceService.batchSave(dto));
    }

    /** 点名名单：包含未点名的学生（status 为 null）。 */
    @Operation(summary = "查看点名名单", description = "返回该课程该日期的选课学生名单，尚未点名的学生 status 为 null")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<List<AttendanceStudentVO>> roster(@Valid AttendanceQueryDTO dto) {
        return Result.success(attendanceService.roster(dto));
    }

    @Operation(summary = "修改考勤记录", description = "只能改状态和备注，唯一键（学生+课程+日期+节次）不可变")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AttendanceUpdateDTO dto) {
        attendanceService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "按课程统计出勤率", description = "返回每个学生的出勤/迟到/早退/请假/旷课次数与出勤率，分母为已点名次数")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/stats")
    public Result<List<AttendanceStatVO>> stats(@RequestParam Long courseId) {
        return Result.success(attendanceService.stats(courseId));
    }

    @Operation(summary = "查询我的考勤", description = "仅学生可用，不接受任何 id 参数——身份来自 token")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public Result<List<AttendanceRecordVO>> my() {
        return Result.success(attendanceService.myAttendance());
    }
}

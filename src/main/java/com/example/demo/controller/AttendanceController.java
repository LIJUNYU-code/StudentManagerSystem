package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.AttendanceBatchRequestDTO;
import com.example.demo.dto.AttendanceQueryDTO;
import com.example.demo.dto.AttendanceUpdateDTO;
import com.example.demo.service.AttendanceService;
import com.example.demo.vo.AttendanceRecordVO;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;
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
@RestController
@AllArgsConstructor
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    /** 批量点名：覆盖语义，重复提交不会报 409。 */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/batch")
    public Result<Integer> batch(@Valid @RequestBody AttendanceBatchRequestDTO dto) {
        return Result.success(attendanceService.batchSave(dto));
    }

    /** 点名名单：包含未点名的学生（status 为 null）。 */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<List<AttendanceStudentVO>> roster(@Valid AttendanceQueryDTO dto) {
        return Result.success(attendanceService.roster(dto));
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AttendanceUpdateDTO dto) {
        attendanceService.update(id, dto);
        return Result.success();
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping("/stats")
    public Result<List<AttendanceStatVO>> stats(@RequestParam Long courseId) {
        return Result.success(attendanceService.stats(courseId));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public Result<List<AttendanceRecordVO>> my() {
        return Result.success(attendanceService.myAttendance());
    }
}

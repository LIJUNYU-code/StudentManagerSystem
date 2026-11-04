package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.EnrollmentQueryDTO;
import com.example.demo.dto.EnrollmentRequestDTO;
import com.example.demo.service.EnrollmentService;
import com.example.demo.vo.EnrollmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "选课管理", description = "学生选课与退课。同一学生同一课程只能选一次，重复选课返回 409")
@RestController
@AllArgsConstructor
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;


    @Operation(summary = "学生选课", description = "同一学生同一课程重复选课返回 409；学生或课程不存在返回 404")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<EnrollmentResponse> add(@Valid @RequestBody EnrollmentRequestDTO user) {
        EnrollmentResponse enrollment = enrollmentService.add(user);
        return Result.success(enrollment);
    }

    @Operation(summary = "按课程分页查询选课名单", description = "courseId 必填；返回结果含学生姓名、课程名等冗余字段方便前端直接展示")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<PageResult<EnrollmentResponse>> listByCourse(@Valid EnrollmentQueryDTO user) {
        PageResult<EnrollmentResponse> page = enrollmentService.page(user);
        return Result.success(page);
    }

    @Operation(summary = "退课", description = "物理删除选课记录（enrollment 是当前状态表）。已录入成绩时退课返回 409")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        enrollmentService.delete(id);
        return Result.success();
    }
}

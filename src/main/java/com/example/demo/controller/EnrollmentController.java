package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.EnrollmentQueryDTO;
import com.example.demo.dto.EnrollmentRequestDTO;
import com.example.demo.service.EnrollmentService;
import com.example.demo.vo.EnrollmentResponse;
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

@RestController
@AllArgsConstructor
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;


    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<EnrollmentResponse> add(@Valid @RequestBody EnrollmentRequestDTO user) {
        EnrollmentResponse enrollment = enrollmentService.add(user);
        return Result.success(enrollment);
    }
    @GetMapping
    public Result<PageResult<EnrollmentResponse>> listByCourse(@Valid EnrollmentQueryDTO user) {
        PageResult<EnrollmentResponse> page = enrollmentService.page(user);
        return Result.success(page);
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        enrollmentService.delete(id);
        return Result.success();
    }
}

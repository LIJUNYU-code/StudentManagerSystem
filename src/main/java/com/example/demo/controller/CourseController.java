package com.example.demo.controller;


import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.CourseQueryDTO;
import com.example.demo.dto.CourseRequestDTO;
import com.example.demo.dto.CourseUpdateRequestDTO;
import com.example.demo.service.CourseService;
import com.example.demo.vo.CourseResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/course")
public class CourseController {

    private final CourseService courseService;

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody CourseRequestDTO courseRequestDTO) {
        courseService.add(courseRequestDTO);
        return Result.success();
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}")
    public Result<CourseResponse> update(@PathVariable Long id, @Valid @RequestBody CourseUpdateRequestDTO courseUpdateRequestDTO) {
        CourseResponse courseResponse = courseService.update(id,courseUpdateRequestDTO);
        return Result.success(courseResponse);
    }

    @GetMapping("/{id}")
    public Result<CourseResponse> query(@PathVariable Long id) {
        CourseResponse courseResponse = courseService.query(id);
        return Result.success(courseResponse);

    }

    @GetMapping
    public Result<PageResult<CourseResponse>> search(@Valid  CourseQueryDTO courseQueryDTO) {
        PageResult<CourseResponse> pageResult = courseService.page(courseQueryDTO);
        return Result.success(pageResult);
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success();
    }




}

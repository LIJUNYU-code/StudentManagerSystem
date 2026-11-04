package com.example.demo.controller;


import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.CourseQueryDTO;
import com.example.demo.dto.CourseRequestDTO;
import com.example.demo.dto.CourseUpdateRequestDTO;
import com.example.demo.service.CourseService;
import com.example.demo.vo.CourseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "课程管理", description = "课程的新增、查询、修改、删除。课程名唯一，重名返回 409")
@RestController
@AllArgsConstructor
@RequestMapping("/course")
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "新建课程", description = "课程名必填且唯一；学分范围 0.5~10")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<CourseResponse> add(@Valid @RequestBody CourseRequestDTO courseRequestDTO) {
        CourseResponse add = courseService.add(courseRequestDTO);
        return Result.success(add);
    }

    @Operation(summary = "更新课程", description = "只更新传入的非空字段；改成已被占用的课程名返回 409")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}")
    public Result<CourseResponse> update(@PathVariable Long id, @Valid @RequestBody CourseUpdateRequestDTO courseUpdateRequestDTO) {
        CourseResponse courseResponse = courseService.update(id,courseUpdateRequestDTO);
        return Result.success(courseResponse);
    }

    @Operation(summary = "查询课程详情", description = "不存在返回 404")
    @GetMapping("/{id}")
    public Result<CourseResponse> query(@PathVariable Long id) {
        CourseResponse courseResponse = courseService.query(id);
        return Result.success(courseResponse);

    }

    @Operation(summary = "分页查询课程", description = "支持 keyword 模糊匹配课程名")
    @GetMapping
    public Result<PageResult<CourseResponse>> search(@Valid  CourseQueryDTO courseQueryDTO) {
        PageResult<CourseResponse> pageResult = courseService.page(courseQueryDTO);
        return Result.success(pageResult);
    }

    @Operation(summary = "删除课程", description = "逻辑删除，删除后再查返回 404")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success();
    }




}

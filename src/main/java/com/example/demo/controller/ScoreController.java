package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.ScoreQueryDTO;
import com.example.demo.dto.ScoreRequestDTO;
import com.example.demo.service.ScoreService;
import com.example.demo.vo.ScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "成绩管理", description = "成绩录入与查询。成绩挂在选课记录上，同一选课同一考试类型只能录一次")
@RestController
@AllArgsConstructor
@RequestMapping("/scores")
public class ScoreController {
    private final ScoreService scoreService;

    @Operation(summary = "录入成绩", description = "按 enrollmentId 录入；同一选课同一 examType 重复录入返回 409。examType 只能是 final / midterm / makeup")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody ScoreRequestDTO dto){
        scoreService.add(dto);
        return Result.success();
    }

    @Operation(summary = "按课程分页查询成绩", description = "courseId 必填；返回结果含学生姓名、学号、课程名等冗余字段")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<PageResult<ScoreResponse>> page(@Valid ScoreQueryDTO dto){
        PageResult<ScoreResponse> page = scoreService.page(dto);
        return Result.success(page);
    }


    @Operation(summary = "查询我的成绩", description = "仅学生可用，不接受任何 id 参数——身份来自 token，防止越权查别人成绩")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public Result<List<ScoreResponse>> query(){
        List<ScoreResponse> result =scoreService.query();
        return Result.success(result);
    }

}

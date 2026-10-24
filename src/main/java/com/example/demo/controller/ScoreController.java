package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.ScoreQueryDTO;
import com.example.demo.dto.ScoreRequestDTO;
import com.example.demo.service.ScoreService;
import com.example.demo.vo.ScoreResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/scores")
public class ScoreController {
    private final ScoreService scoreService;

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody ScoreRequestDTO dto){
        scoreService.add(dto);
        return Result.success();
    }

    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<PageResult<ScoreResponse>> page(@Valid ScoreQueryDTO dto){
        PageResult<ScoreResponse> page = scoreService.page(dto);
        return Result.success(page);
    }


    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public Result<List<ScoreResponse>> query(){
        List<ScoreResponse> result =scoreService.query();
        return Result.success(result);
    }

}

package com.example.demo.controller;

import com.example.demo.vo.ClazzResponse;
import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.ClazzQueryDTO;
import com.example.demo.dto.ClazzRequestDTO;
import com.example.demo.dto.ClazzUpdateRequestDTO;
import com.example.demo.service.ClazzService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clazz")
@AllArgsConstructor
public class ClazzController {

    private final ClazzService clazzService;
    @PostMapping
    public Result<Void> addClazz(@Valid @RequestBody ClazzRequestDTO clazzRequestDTO ){
        clazzService.add(clazzRequestDTO);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ClazzResponse> queryClazz(@PathVariable Long id){
        return Result.success(clazzService.query(id));
    }

    @GetMapping
    public Result<PageResult<ClazzResponse>> page(@Valid  ClazzQueryDTO clazzQueryDTO){
        return Result.success(clazzService.page(clazzQueryDTO));
    }
    @PutMapping("/{id}")
    public Result<ClazzResponse> update(@PathVariable Long id,
                         @Valid@RequestBody ClazzUpdateRequestDTO clazzUpdateRequestDTO){
        ClazzResponse newClazz = clazzService.update(id, clazzUpdateRequestDTO);
        return Result.success(newClazz);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        clazzService.delete(id);
        return Result.success();
    }
}

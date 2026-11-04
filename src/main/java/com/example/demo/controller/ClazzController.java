package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.ClazzQueryDTO;
import com.example.demo.dto.ClazzRequestDTO;
import com.example.demo.dto.ClazzUpdateRequestDTO;
import com.example.demo.service.ClazzService;
import com.example.demo.vo.ClazzResponse;
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


@Tag(name = "班级管理", description = "班级的新增、查询、修改、删除。班级名唯一，重名返回 409")
@RestController
@RequestMapping("/clazz")
@AllArgsConstructor
public class ClazzController {

    private final ClazzService clazzService;

    @Operation(summary = "新建班级", description = "班级名必填且唯一；重名返回 409")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping
    public Result<Void> addClazz(@Valid @RequestBody ClazzRequestDTO clazzRequestDTO ){
        clazzService.add(clazzRequestDTO);
        return Result.success();
    }

    @Operation(summary = "查询班级详情", description = "不存在返回 404")
    @GetMapping("/{id}")
    public Result<ClazzResponse> queryClazz(@PathVariable Long id){
        return Result.success(clazzService.query(id));
    }

    @Operation(summary = "分页查询班级", description = "支持 keyword 模糊匹配班级名")
    @GetMapping
    public Result<PageResult<ClazzResponse>> page(@Valid  ClazzQueryDTO clazzQueryDTO){
        return Result.success(clazzService.page(clazzQueryDTO));
    }

    @Operation(summary = "更新班级", description = "只更新传入的非空字段；改成已被占用的班级名返回 409；全部字段为空返回 400")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}")
    public Result<ClazzResponse> update(@PathVariable Long id,
                         @Valid@RequestBody ClazzUpdateRequestDTO clazzUpdateRequestDTO){
        ClazzResponse newClazz = clazzService.update(id, clazzUpdateRequestDTO);
        return Result.success(newClazz);
    }


    @Operation(summary = "删除班级", description = "逻辑删除，删除后再查返回 404")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        clazzService.delete(id);
        return Result.success();
    }
}

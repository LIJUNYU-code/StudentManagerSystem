package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.StudentResponse;
import com.example.demo.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生接口。
 * 统一约定：
 * - 创建/登录类动作用 POST（登录本质是“创建一个令牌”，PUT 语义不合）
 * - 方法名小写驼峰；返回统一 Result 结构
 */
@RestController
@RequestMapping("/student")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequestDTO dto) {
        studentService.register(dto);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return Result.success(studentService.login(dto));
    }

    @GetMapping("/{id}")
    public Result<StudentResponse> getById(@PathVariable Long id) {
        return Result.success(studentService.getById(id));
    }
}

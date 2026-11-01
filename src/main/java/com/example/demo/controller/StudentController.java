package com.example.demo.controller;

import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.ResetPasswordRequestDTO;
import com.example.demo.dto.StudentQueryDTO;
import com.example.demo.dto.StudentUpdateRequestDTO;
import com.example.demo.service.StudentService;
import com.example.demo.vo.StudentResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生接口。
 * 统一约定：
 * - 创建/登录类动作用 POST（登录本质是“创建一个令牌”，PUT 语义不合）
 * - 更新用 PUT，删除用 DELETE，查询用 GET
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

    /** 分页 + 条件查询（GET + 查询字符串，例如 /student?page=1&size=10&keyword=张） */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @GetMapping
    public Result<PageResult<StudentResponse>> page(@Valid StudentQueryDTO query) {
        return Result.success(studentService.page(query));
    }

    /**
     * 按 id 查学生详情。
     * 权限：教师/管理员可查任意学生；学生只能查自己（id 必须等于 token 主体），
     * 否则学生可以遍历 id 把全校学生资料拉走。
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','STUDENT')")
    @GetMapping("/{id}")
    public Result<StudentResponse> getById(@PathVariable Long id) {
        return Result.success(studentService.getByIdForCurrentUser(id));
    }

    /** 更新学生信息（只更新传入的非空字段） */



    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PutMapping("/{id}")
    public Result<StudentResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody StudentUpdateRequestDTO dto) {
        return Result.success(studentService.update(id, dto));
    }

    /** 逻辑删除学生 */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.delete(id);
        return Result.success();
    }

    /**
     * 教师/管理员重置学生密码（学生忘记密码时用，不需要旧密码）。
     * 学生本人改密走 POST /auth/password（需要旧密码），两条路互不重叠。
     */
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequestDTO dto) {
        studentService.resetPassword(id, dto.getNewPassword());
        return Result.success();
    }
}

package com.example.demo.controller;


import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.UserReviewQueryDTO;
import com.example.demo.service.UserService;
import com.example.demo.vo.AdminUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户审核", description = "管理员专用：审核教师注册申请、禁用或启用账号。整个 Controller 要求 ADMIN 角色")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/admin/user")
@AllArgsConstructor
public class AdminUserController {

    private final UserService userService;


    @Operation(summary = "分页查询用户", description = "可按 keyword 搜索用户名/姓名、按 status 筛选（0 待审核 / 1 正常 / 2 禁用）")
    @GetMapping
    public Result<PageResult<AdminUserResponse>> page(@Valid UserReviewQueryDTO userReviewQueryDTO) {
        return Result.success(userService.page(userReviewQueryDTO));
    }

    @Operation(summary = "审核通过", description = "把待审核账号（status=0）改为正常（status=1），通过后该账号即可登录")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id){
        userService.approve(id);
        return Result.success();
    }

    @Operation(summary = "禁用账号", description = "把账号改为禁用状态（status=2），禁用后无法登录")
    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id){
        userService.disable(id);
        return Result.success();
    }
}

package com.example.demo.controller;


import com.example.demo.common.PageResult;
import com.example.demo.common.Result;
import com.example.demo.dto.UserReviewQueryDTO;
import com.example.demo.service.UserService;
import com.example.demo.vo.AdminUserResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/admin/user")
@AllArgsConstructor
public class AdminUserController {

    private final UserService userService;


    @GetMapping
    public Result<PageResult<AdminUserResponse>> page(@Valid UserReviewQueryDTO userReviewQueryDTO) {
        return Result.success(userService.page(userReviewQueryDTO));
    }

    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id){
        userService.approve(id);
        return Result.success();
    }

    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id){
        userService.disable(id);
        return Result.success();
    }
}

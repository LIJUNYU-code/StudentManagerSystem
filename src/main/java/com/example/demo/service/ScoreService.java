package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.ScoreQueryDTO;
import com.example.demo.dto.ScoreRequestDTO;
import com.example.demo.vo.ScoreResponse;

import java.util.List;

/**
 * 成绩业务接口。
 * <p>
 * 参数校验注解（@Valid）只写在 Controller 的入参上——那才是 Spring MVC 真正校验的位置。
 * 在接口方法上再标 @Valid 不会触发任何校验，属于会误导人的死代码，故删除。
 */
public interface ScoreService {
    void add(ScoreRequestDTO dto);

    PageResult<ScoreResponse> page(ScoreQueryDTO dto);

    List<ScoreResponse> query();
}

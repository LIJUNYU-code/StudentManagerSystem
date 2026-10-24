package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.ScoreQueryDTO;
import com.example.demo.dto.ScoreRequestDTO;
import com.example.demo.vo.ScoreResponse;
import jakarta.validation.Valid;

import java.util.List;

public interface ScoreService {
    void add(@Valid ScoreRequestDTO dto);

    PageResult<ScoreResponse> page(@Valid ScoreQueryDTO dto);

    List<ScoreResponse> query();
}

package com.example.demo.service;

import com.example.demo.vo.ClazzResponse;
import com.example.demo.common.PageResult;
import com.example.demo.dto.ClazzQueryDTO;
import com.example.demo.dto.ClazzRequestDTO;
import com.example.demo.dto.ClazzUpdateRequestDTO;

public interface ClazzService {
    void add(ClazzRequestDTO dto);

    ClazzResponse query(Long id);

    PageResult<ClazzResponse> page(ClazzQueryDTO query);

    ClazzResponse update(Long id, ClazzUpdateRequestDTO dto);

    void delete(Long id);
}

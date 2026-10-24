package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.EnrollmentQueryDTO;
import com.example.demo.dto.EnrollmentRequestDTO;
import com.example.demo.vo.EnrollmentResponse;
import jakarta.validation.Valid;

public interface EnrollmentService {
    EnrollmentResponse add(@Valid EnrollmentRequestDTO user);

    PageResult<EnrollmentResponse> page(@Valid EnrollmentQueryDTO user);

    void delete(Long id);
}

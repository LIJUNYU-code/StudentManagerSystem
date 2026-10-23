package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.CourseQueryDTO;
import com.example.demo.dto.CourseRequestDTO;
import com.example.demo.dto.CourseUpdateRequestDTO;
import com.example.demo.vo.CourseResponse;
import jakarta.validation.Valid;

public interface CourseService {

    CourseResponse query(Long id);

    void add(CourseRequestDTO courseRequestDTO);

    CourseResponse update( Long id ,@Valid CourseUpdateRequestDTO courseUpdateRequestDTO);

    PageResult<CourseResponse> page(@Valid CourseQueryDTO courseQueryDTO);

    void delete(Long id);
}

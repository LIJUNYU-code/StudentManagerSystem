package com.example.demo.service;

import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.StudentDTO;
import com.example.demo.entity.Student;

public interface StudentService {

    public Student selectById(Long id);

    Student findByName(String name);

    void register(StudentDTO studentDTO);

    LoginResponseDTO login(StudentDTO studentDTO);
}

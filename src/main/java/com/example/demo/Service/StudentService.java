package com.example.demo.Service;

import com.example.demo.DTO.LoginResponseDTO;
import com.example.demo.DTO.StudentDTO;
import com.example.demo.Entity.Student;

public interface StudentService {

    public Student selectById(Long id);

    Student findByName(String name);

    void register(StudentDTO studentDTO);

    LoginResponseDTO login(StudentDTO studentDTO);
}

package com.example.demo.Controller;

import com.example.demo.DTO.LoginResponseDTO;
import com.example.demo.DTO.StudentDTO;
import com.example.demo.Day1.Result;
import com.example.demo.Entity.Student;
import com.example.demo.Service.StudentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
public class StudentController {
    @Autowired
    private StudentService studentService;

    @PostMapping("/register")
    public Result RegisterStudent(@Valid @RequestBody StudentDTO studentDTO) {
       studentService.register(studentDTO);
       return Result.success();
    }

    @PutMapping("/login")
    public Result Login(@RequestBody StudentDTO studentDTO){
        LoginResponseDTO response = studentService.login(studentDTO);
        return Result.success(response);
    }

    @GetMapping("/{id}")
    public Result SelectStudent(@PathVariable Long id) {
        Student student = studentService.selectById(id);
        return Result.success(student);
    }
}

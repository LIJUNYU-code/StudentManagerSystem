package com.example.demo.Service.ServiceImpl;

import com.example.demo.DTO.LoginResponseDTO;
import com.example.demo.DTO.StudentDTO;
import com.example.demo.Entity.Student;
import com.example.demo.Exception.NameOrPasswordError;
import com.example.demo.Exception.StudentHasExisit;
import com.example.demo.Exception.StudentNotFoundException;
import com.example.demo.Mapper.StudentMapper;
import com.example.demo.Service.StudentService;
import com.example.demo.Utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@org.springframework.stereotype.Service
public class StudentServiceImpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;



    @Override
    public Student selectById(Long id) {
        Student student = studentMapper.selectById(id);
        if(student == null){
            throw new StudentNotFoundException("用户不存在："+id);
        }
        return student;
    }

    @Override
    public Student findByName(String name) {
        Student student = studentMapper.selectByName(name);
        return student;
    }
    @Transactional
    @Override
    public void register(StudentDTO studentDTO) {
        Student student = findByName(studentDTO.getName());
        if (student !=null )  throw new StudentHasExisit("学生已存在");


        String password = passwordEncoder.encode(studentDTO.getPassword());
        student = Student.builder()
                .age(studentDTO.getAge())
                .name(studentDTO.getName())
                .password(password)
                .phone(studentDTO.getPhone())
                .gender(studentDTO.getGender())
                .createTime(LocalDate.now())
                .updateTime(LocalDate.now())
                .build();

        studentMapper.insert(student);
    }

    @Override
    public LoginResponseDTO login(StudentDTO studentDTO) {
        Student student = findByName(studentDTO.getName());
        if (student == null) {
            throw new StudentNotFoundException("用户名或密码错误！");
        }
        if (!passwordEncoder.matches(studentDTO.getPassword(), student.getPassword())) {
            throw new NameOrPasswordError("用户名或密码错误！");
        }
        String token = jwtUtil.generateToken(student.getId(), student.getName());
        return  LoginResponseDTO
                .builder()
                .id(student.getId())
                .name(student.getName())
                .token(token)
                .build();


    }
}

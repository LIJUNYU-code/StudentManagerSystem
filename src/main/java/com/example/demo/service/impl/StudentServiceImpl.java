package com.example.demo.service.impl;

import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.StudentResponse;
import com.example.demo.entity.Student;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.StudentAlreadyExistsException;
import com.example.demo.exception.StudentNotFoundException;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.service.StudentService;
import com.example.demo.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生业务实现。
 * 依赖注入用构造器（final 字段），不用字段 @Autowired：
 * 依赖一目了然、无法构造出依赖不全的实例、也方便单测时手动传 mock。
 */
@Service
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public StudentServiceImpl(StudentMapper studentMapper,
                              PasswordEncoder passwordEncoder,
                              JwtUtil jwtUtil) {
        this.studentMapper = studentMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public StudentResponse getById(Long id) {
        return StudentResponse.from(getEntityById(id));
    }

    @Override
    public Student getEntityById(Long id) {
        // selectById 自动携带 deleted=0 条件（@TableLogic），已删除的学生查不到
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new StudentNotFoundException("学生不存在: " + id);
        }
        return student;
    }

    @Override
    public Student findByStudentNo(String studentNo) {
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo));
    }

    @Transactional
    @Override
    public void register(RegisterRequestDTO dto) {
        // 应用层查重给出友好提示；并发漏网由 uk_student_no 唯一索引兜底，
        // DuplicateKeyException 在 GlobalExceptionHandler 统一翻译成 409
        Long count = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, dto.getStudentNo()));
        if (count != null && count > 0) {
            throw new StudentAlreadyExistsException("学号已注册: " + dto.getStudentNo());
        }

        Student student = Student.builder()
                .studentNo(dto.getStudentNo())
                .name(dto.getName())
                .gender(dto.getGender())
                .age(dto.getAge())
                .phone(dto.getPhone())
                // 只存哈希，永远不存明文
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();
        // createTime/updateTime 由 MyMetaObjectHandler 自动填充
        try {
            studentMapper.insert(student);
        } catch (DuplicateKeyException e) {
            // 事务内尽早翻译，避免到达兜底处理器时丢失具体语义
            throw new StudentAlreadyExistsException("学号已注册: " + dto.getStudentNo());
        }
    }

    @Override
    public LoginResponseDTO login(LoginRequestDTO dto) {
        // 学号不存在与密码错误给同一个提示，不向攻击者透露“该学号已注册”
        Student student = findByStudentNo(dto.getStudentNo());
        if (student == null || !passwordEncoder.matches(dto.getPassword(), student.getPassword())) {
            throw new InvalidCredentialsException("学号或密码错误");
        }
        String token = jwtUtil.generateToken(student.getId(), student.getName());
        return LoginResponseDTO.builder()
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .name(student.getName())
                .token(token)
                .build();
    }
}

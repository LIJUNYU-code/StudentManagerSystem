package com.example.demo.service.impl;

import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.StudentUpdateRequestDTO;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.StudentAlreadyExistsException;
import com.example.demo.exception.StudentNotFoundException;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * StudentServiceImpl 单元测试。
 * <p>
 * 核心手法：@Mock 伪造 StudentMapper / PasswordEncoder / JwtUtil——
 * 不连数据库、不算真 BCrypt、不签真 JWT，全部按测试剧本返回排练好的数据，
 * 从而只验证 StudentServiceImpl 自己的业务逻辑（这就是"单元"测试的含义）。
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private StudentServiceImpl service;

    @BeforeEach
    void setUp() {
        // 手动 new，把三个 mock 塞进构造器——构造器注入的红利在这里兑现
        service = new StudentServiceImpl(studentMapper, passwordEncoder, jwtUtil);
    }

    /** 造一个数据库里"存在"的学生（mock 剧本常用道具） */
    private Student existingStudent() {
        Student s = new Student();
        s.setId(7L);
        s.setStudentNo("S202500001");
        s.setName("张三");
        s.setPassword("$2a$10$hashedPasswordFromDb");
        return s;
    }

    private RegisterRequestDTO registerDto() {
        RegisterRequestDTO dto = new RegisterRequestDTO();
        dto.setStudentNo("S202500001");
        dto.setName("张三");
        dto.setGender(1);
        dto.setAge(18);
        dto.setPhone("13800000000");
        dto.setPassword("Abc12345");
        return dto;
    }

    // ========== getById ==========

    @Test
    @DisplayName("查询不存在的学生 → 抛 StudentNotFoundException")
    void getById_notFound() {
        when(studentMapper.selectById(99L)).thenReturn(null);

        assertThrows(StudentNotFoundException.class, () -> service.getById(99L));
    }

    // ========== register ==========

    @Test
    @DisplayName("注册：学号已存在 → 抛 StudentAlreadyExistsException，且绝不执行 insert")
    void register_duplicateStudentNo() {
        when(studentMapper.selectCount(any())).thenReturn(1L);

        assertThrows(StudentAlreadyExistsException.class,
                () -> service.register(registerDto()));

        verify(studentMapper, never()).insert(any(Student.class));
    }

    @Test
    @DisplayName("注册成功：密码被加密存储（绝不明文），insert 被调用")
    void register_success_encodesPassword() {
        when(studentMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("Abc12345")).thenReturn("$2a$10$encoded");
        when(studentMapper.insert(any(Student.class))).thenReturn(1);

        service.register(registerDto());

        // 捕获实际 insert 的对象，断言存进去的是密文
        org.mockito.ArgumentCaptor<Student> captor =
                org.mockito.ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).insert(captor.capture());
        assertEquals("$2a$10$encoded", captor.getValue().getPassword());
        assertNotEquals("Abc12345", captor.getValue().getPassword());
    }

    @Test
    @DisplayName("注册并发漏网：insert 抛 DuplicateKeyException → 翻译成 StudentAlreadyExistsException")
    void register_duplicateKeyTranslated() {
        when(studentMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        when(studentMapper.insert(any(Student.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_student_no"));

        assertThrows(StudentAlreadyExistsException.class,
                () -> service.register(registerDto()));
    }

    // ========== login ==========

    @Test
    @DisplayName("登录：学号不存在 → InvalidCredentialsException（不透露学号是否注册过）")
    void login_unknownStudentNo() {
        when(studentMapper.selectOne(any())).thenReturn(null);

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDTO("S202500001", "Abc12345")));
    }

    @Test
    @DisplayName("登录：密码错误 → InvalidCredentialsException")
    void login_wrongPassword() {
        Student s = existingStudent();
        when(studentMapper.selectOne(any())).thenReturn(s);
        when(passwordEncoder.matches("wrongPass", s.getPassword())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequestDTO("S202500001", "wrongPass")));
    }

    @Test
    @DisplayName("登录成功：签发 token，返回 id/studentNo/name/token")
    void login_success() {
        Student s = existingStudent();
        when(studentMapper.selectOne(any())).thenReturn(s);
        when(passwordEncoder.matches("Abc12345", s.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(7L, "张三", "student")).thenReturn("fake.jwt.token");

        LoginResponseDTO resp = service.login(new LoginRequestDTO("S202500001", "Abc12345"));

        assertEquals(7L, resp.getId());
        assertEquals("S202500001", resp.getStudentNo());
        assertEquals("张三", resp.getName());
        assertEquals("fake.jwt.token", resp.getToken());
    }

    // ========== update ==========

    @Test
    @DisplayName("更新：全空字段 → BusinessException(400)，且不执行 updateById")
    void update_emptyBody() {
        when(studentMapper.selectById(7L)).thenReturn(existingStudent());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(7L, new StudentUpdateRequestDTO()));
        assertEquals(400, e.getCode());
        verify(studentMapper, never()).updateById(any(Student.class));
    }

    @Test
    @DisplayName("更新：学号被别人占用 → StudentAlreadyExistsException")
    void update_studentNoTaken() {
        when(studentMapper.selectById(7L)).thenReturn(existingStudent());
        when(studentMapper.selectCount(any())).thenReturn(1L);

        StudentUpdateRequestDTO dto = new StudentUpdateRequestDTO();
        dto.setStudentNo("S202500002");

        assertThrows(StudentAlreadyExistsException.class, () -> service.update(7L, dto));
    }

    @Test
    @DisplayName("更新成功：只带 name → updateById 被调用一次，返回最新数据")
    void update_success() {
        Student dbStudent = existingStudent();
        when(studentMapper.selectById(7L)).thenReturn(dbStudent);
        // 第二次 selectById（回读最新值）返回改了名的对象
        Student renamed = existingStudent();
        renamed.setName("李四");
        when(studentMapper.updateById(any(Student.class))).thenReturn(1);

        StudentUpdateRequestDTO dto = new StudentUpdateRequestDTO();
        dto.setName("李四");

        // selectById 第二次被调用时返回 renamed（thenReturn 连写 = 依次返回）
        when(studentMapper.selectById(7L)).thenReturn(dbStudent, renamed);

        var resp = service.update(7L, dto);

        assertEquals("李四", resp.getName());
        verify(studentMapper).updateById(any(Student.class));
    }
}

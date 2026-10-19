package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.StudentQueryDTO;
import com.example.demo.dto.StudentResponse;
import com.example.demo.dto.StudentUpdateRequestDTO;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.StudentAlreadyExistsException;
import com.example.demo.exception.StudentNotFoundException;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.service.StudentService;
import com.example.demo.util.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Override
    public PageResult<StudentResponse> page(StudentQueryDTO query) {
        // LIKE 的两个通配通配合一起处理：先用 % 包裹关键词
        String keyword = query.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String likeKeyword = hasKeyword ? "%" + keyword.trim() + "%" : null;

        Page<Student> page = studentMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<Student>()
                        // 姓名或学号模糊匹配（OR 两侧条件各自独立）
                        .and(hasKeyword, w -> w
                                .like(Student::getName, likeKeyword)
                                .or()
                                .like(Student::getStudentNo, likeKeyword))
                        .eq(query.getClazzId() != null, Student::getClazzId, query.getClazzId())
                        // 排序：新注册的学生排前面，浏览体验更直观
                        .orderByDesc(Student::getId));

        // 实体→VO 逐条转换，绝不让实体（含密码哈希、deleted）直接出接口
        List<StudentResponse> records = page.getRecords().stream()
                .map(StudentResponse::from)
                .toList();
        return new PageResult<>(page.getTotal(), records, page.getCurrent(), page.getSize());
    }

    @Transactional
    @Override
    public StudentResponse update(Long id, StudentUpdateRequestDTO dto) {
        // 确保学生存在且未删除，顺便拿到原数据
        Student student = getEntityById(id);

        // 至少要改一个字段，避免“空更新”请求
        boolean hasChange = dto.getStudentNo() != null || dto.getName() != null
                || dto.getGender() != null || dto.getAge() != null
                || dto.getPhone() != null || dto.getClazzId() != null;
        if (!hasChange) {
            throw new BusinessException(400, "至少提供一个要修改的字段");
        }

        // 若改学号，先查重；并发漏网由 uk_student_no 唯一索引兜底（DuplicateKey→409）
        if (dto.getStudentNo() != null && !dto.getStudentNo().equals(student.getStudentNo())) {
            Long count = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getStudentNo, dto.getStudentNo())
                    // 排除自己：否则改回自己的学号也会被误判重复
                    .ne(Student::getId, id));
            if (count != null && count > 0) {
                throw new StudentAlreadyExistsException("学号已注册: " + dto.getStudentNo());
            }
        }

        // updateById 只 SET 非 null 字段（update-strategy 默认 NOT_NULL）
        Student changes = new Student();
        changes.setId(id);
        changes.setStudentNo(dto.getStudentNo());
        changes.setName(dto.getName());
        changes.setGender(dto.getGender());
        changes.setAge(dto.getAge());
        changes.setPhone(dto.getPhone());
        changes.setClazzId(dto.getClazzId());
        studentMapper.updateById(changes);

        // 返回更新后的完整数据，前端免二次查询
        return StudentResponse.from(getEntityById(id));
    }

    @Override
    public void delete(Long id) {
        // 先查再删：保证删除不存在的 id 返回 404 而不是“假装删掉了”
        getEntityById(id);
        // @TableLogic 让 deleteById 实际执行 UPDATE deleted=1
        studentMapper.deleteById(id);
    }
}

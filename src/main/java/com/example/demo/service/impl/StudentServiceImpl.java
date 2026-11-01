package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.StudentQueryDTO;
import com.example.demo.dto.StudentUpdateRequestDTO;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.StudentAlreadyExistsException;
import com.example.demo.exception.StudentNotFoundException;
import com.example.demo.mapper.ClazzMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.service.StudentService;
import com.example.demo.util.JwtUtil;
import com.example.demo.vo.StudentResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ClazzMapper clazzMapper;

    public StudentServiceImpl(StudentMapper studentMapper,
                              PasswordEncoder passwordEncoder,
                              JwtUtil jwtUtil,
                              ClazzMapper clazzMapper) {
        this.studentMapper = studentMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.clazzMapper = clazzMapper;
    }

    @Override
    public StudentResponse getById(Long id) {
        return StudentResponse.from(getEntityById(id));
    }

    /**
     * 带"查看资格"校验的查询：教师/管理员可查任意学生，学生只能查自己。
     * <p>
     * 为什么单独开一个方法、而不是把校验塞进 getEntityById：
     * getEntityById 还被 update / delete / resetPassword 内部复用，
     * 那些入口的权限由 @PreAuthorize 保证（只有教师/管理员能进），
     * 若把"只能看自己"写进 getEntityById，教师改学生资料会被自己拦成 403。
     */
    @Override
    public StudentResponse getByIdForCurrentUser(Long id) {
        if (isCurrentUserStudentOnly() && !currentUserId().equals(id)) {
            throw new BusinessException(403, "只能查看自己的信息");
        }
        return StudentResponse.from(getEntityById(id));
    }

    /** 当前登录者是否"只有学生身份"（教师/管理员任一角色即放行） */
    private boolean isCurrentUserStudentOnly() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return true; // 无身份信息时按最严格处理
        }
        boolean teacherOrAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_TEACHER".equals(a) || "ROLE_ADMIN".equals(a));
        return !teacherOrAdmin;
    }

    /** 从 SecurityContext 取当前用户 id（JWT 过滤器写入的 Long） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new BusinessException(401, "登录状态异常，请重新登录");
        }
        return id;
    }

    /**
     * 教师/管理员重置学生密码（学生忘记密码的场景，不需要旧密码）。
     * 权限由 Controller 的 @PreAuthorize 保证。
     */
    @Transactional
    @Override
    public void resetPassword(Long id, String newPassword) {
        getEntityById(id); // 不存在直接 404
        Student changes = new Student();
        changes.setId(id);
        changes.setPassword(passwordEncoder.encode(newPassword));
        studentMapper.updateById(changes);
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
        String token = jwtUtil.generateToken(student.getId(), student.getName(),"student");
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

        // 若改班级，先确认班级真实存在：
        // clazz_id 是逻辑外键（没有物理 FK 约束），不校验就会存进一个指向空气的班级 id，
        // 之后学生列表按班级筛选、班级人数统计都会出现查不到的情况。
        if (dto.getClazzId() != null && clazzMapper.selectById(dto.getClazzId()) == null) {
            throw new BusinessException(400, "班级不存在: " + dto.getClazzId());
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

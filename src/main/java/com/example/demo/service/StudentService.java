package com.example.demo.service;

import com.example.demo.common.PageResult;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponseDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.dto.StudentQueryDTO;
import com.example.demo.dto.StudentResponse;
import com.example.demo.dto.StudentUpdateRequestDTO;
import com.example.demo.entity.Student;

/**
 * 学生业务接口。
 * 入参用专门的请求 DTO，出参用不含敏感字段的 VO——
 * 不要让 Web 层的形状直接绑死在数据库实体上。
 */
public interface StudentService {

    /** 按 id 查询学生（不含密码哈希） */
    StudentResponse getById(Long id);

    /** 按 id 查询实体（内部用，返回原始实体） */
    Student getEntityById(Long id);

    /** 按学号查询学生，不存在返回 null */
    Student findByStudentNo(String studentNo);

    /** 学生自助注册 */
    void register(RegisterRequestDTO dto);

    /** 学生登录（学号 + 密码） */
    LoginResponseDTO login(LoginRequestDTO dto);

    /** 分页 + 条件查询学生列表 */
    PageResult<StudentResponse> page(StudentQueryDTO query);

    /** 更新学生信息（只更新传入的非空字段） */
    StudentResponse update(Long id, StudentUpdateRequestDTO dto);

    /** 逻辑删除学生 */
    void delete(Long id);
}

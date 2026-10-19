package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学生信息更新请求。
 * <p>
 * 所有字段可选：只更新前端传了的字段（null = 不更新）。
 * 这是 PUT/PATCH 的常用折中方案：班级管理里改个年龄不必把整个学生资料都传一遍。
 * Bean Validation 对“有值才校验”天然友好——@Pattern 等约束对 null 一律放行，
 * 真正的“至少改一个字段”由 Service 层校验。
 */
public class StudentUpdateRequestDTO {

    @Pattern(regexp = "^S\\d{9}$", message = "学号格式为 S 开头 + 9 位数字，例如 S2023000001")
    private String studentNo;

    @Size(min = 2, max = 100, message = "姓名长度 2~100 个字符")
    private String name;

    @Min(value = 0, message = "性别只能是 0（男）或 1（女）")
    @Max(value = 1, message = "性别只能是 0（男）或 1（女）")
    private Integer gender;

    @Min(value = 6, message = "年龄范围 6~100")
    @Max(value = 100, message = "年龄范围 6~100")
    private Integer age;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 修改学生所属班级（null = 不改） */
    private Long clazzId;

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Long getClazzId() {
        return clazzId;
    }

    public void setClazzId(Long clazzId) {
        this.clazzId = clazzId;
    }
}

package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生信息更新请求。
 * <p>
 * 所有字段可选：只更新前端传了的字段（null = 不更新）。
 * 这是 PUT/PATCH 的常用折中方案：班级管理里改个年龄不必把整个学生资料都传一遍。
 * Bean Validation 对“有值才校验”天然友好——@Pattern 等约束对 null 一律放行，
 * 真正的“至少改一个字段”由 Service 层校验。
 */
@Schema(description = "更新学生信息请求：所有字段可选，null 表示不修改该字段")
@Data
public class StudentUpdateRequestDTO {

    @Schema(description = "学号，改成已被其他学生占用的学号返回 409", example = "S202300001")
    @Pattern(regexp = "^S\\d{9}$", message = "学号格式为 S 开头 + 9 位数字，例如 S2023000001")
    private String studentNo;

    @Schema(description = "姓名，2~100 个字符", example = "张三")
    @Size(min = 2, max = 100, message = "姓名长度 2~100 个字符")
    private String name;

    @Schema(description = "性别：0=男，1=女", allowableValues = {"0", "1"}, example = "1")
    @Min(value = 0, message = "性别只能是 0（男）或 1（女）")
    @Max(value = 1, message = "性别只能是 0（男）或 1（女）")
    private Integer gender;

    @Schema(description = "年龄，6~100", example = "20")
    @Min(value = 6, message = "年龄范围 6~100")
    @Max(value = 100, message = "年龄范围 6~100")
    private Integer age;

    @Schema(description = "手机号，11 位中国大陆号码", example = "13800000000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 修改学生所属班级（null = 不改） */
    @Schema(description = "所属班级 id，null 表示不改", example = "1")
    private Long clazzId;


}

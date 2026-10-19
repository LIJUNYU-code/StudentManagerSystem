package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 学生注册请求参数 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^S\\d{9}$", message = "学号格式为 S 开头 + 9 位数字，例如 S2023000001")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 100, message = "姓名长度必须为2到100位")
    private String name;

    /** 性别：0-男 1-女，可空 */
    @Min(value = 0, message = "性别只能为0或1")
    @Max(value = 1, message = "性别只能为0或1")
    private Integer gender;

    @Min(value = 6, message = "年龄必须大于等于6")
    @Max(value = 100, message = "年龄必须小于等于100")
    private Integer age;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\S{6,18}$", message = "密码长度必须为6到18位，且不能包含空白字符")
    private String password;
}

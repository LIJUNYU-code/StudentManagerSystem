package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "学生注册请求")
public class RegisterRequestDTO {

    @Schema(description = "学号，S 开头 + 9 位数字，注册后不可重复", example = "S202300001")
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^S\\d{9}$", message = "学号格式为 S 开头 + 9 位数字，例如 S2023000001")
    private String studentNo;

    @Schema(description = "学生姓名，2~100 个字符。姓名允许重名，身份靠学号区分", example = "张三")
    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 100, message = "姓名长度必须为2到100位")
    private String name;

    /** 性别：0-男 1-女，可空 */
    @Schema(description = "性别：0=男，1=女，不传则未知", example = "1")
    @Min(value = 0, message = "性别只能为0或1")
    @Max(value = 1, message = "性别只能为0或1")
    private Integer gender;

    @Schema(description = "年龄，6~100，可选", example = "20")
    @Min(value = 6, message = "年龄必须大于等于6")
    @Max(value = 100, message = "年龄必须小于等于100")
    private Integer age;

    @Schema(description = "手机号，11 位中国大陆号码", example = "13800000000")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Schema(description = "登录密码，6~18 位且不能含空白字符。服务端用 BCrypt 加密存储", example = "abc12345")
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\S{6,18}$", message = "密码长度必须为6到18位，且不能包含空白字符")
    private String password;
}

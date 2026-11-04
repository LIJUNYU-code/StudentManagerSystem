package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "新建班级请求")
@Data
public class ClazzRequestDTO {

    @Schema(description = "班级名，2~100 个字符，全局唯一，重名返回 409", example = "高一(1)班")
    @NotBlank(message = "班级名不能为空")
    @Size(min = 2, max = 100, message = "班级名长度必须为2到100位")
    private String name;

    @Schema(description = "年级，可选", example = "高一")
    private String grade;

    @Schema(description = "班主任的用户 id，可选。目前只做存储，不做关联校验", example = "2")
    private Integer teacherId;

}

package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "更新班级请求：所有字段可选，null 表示不修改该字段")
@Data
public class ClazzUpdateRequestDTO {

    @Schema(description = "班级名，改成已被其他班级占用的名字返回 409", example = "高一(2)班")
    @Size(min = 2, max = 100, message = "班级名长度 2~100 个字符")
    private String name;

    @Schema(description = "年级", example = "高一")
    private String grade;

    @Schema(description = "班主任的用户 id", example = "2")
    private Integer teacherId;

}

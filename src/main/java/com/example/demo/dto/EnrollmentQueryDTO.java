package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EnrollmentQueryDTO {

    private Long courseId;
    @Min(value = 1,message = "页码最小数为 1")
    private Integer page = 1;
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100 , message = "每页条数最大数为100")
    private Integer pageSize = 10;

}

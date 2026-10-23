package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
    @Select("select * from course where name = #{name} and teacher_id = #{teacherId}")
    Course selectByNameAndTeacherId(@NotBlank @Size(max = 100) String name, Integer teacherId);
}

package com.example.demo.Mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.Entity.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
    @Select("select * from student where name = #{name}")
    Student selectByName(String name);
}

package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * Student 表 Mapper。
 * 继承 BaseMapper 后自动拥有 insert/selectById/selectPage/update/delete 等
 * 通用方法；简单的条件查询直接用 LambdaQueryWrapper，不需要手写 SQL。
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}

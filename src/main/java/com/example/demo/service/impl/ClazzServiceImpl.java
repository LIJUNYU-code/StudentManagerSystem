package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.ClazzQueryDTO;
import com.example.demo.dto.ClazzRequestDTO;
import com.example.demo.dto.ClazzUpdateRequestDTO;
import com.example.demo.entity.Clazz;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.ClazzMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.service.ClazzService;
import com.example.demo.vo.ClazzResponse;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class ClazzServiceImpl implements ClazzService {
    private final ClazzMapper clazzMapper;
    private final StudentMapper studentMapper;


    @Override
    @Transactional
    public void add(ClazzRequestDTO dto) {
        Long count = clazzMapper.selectCount(new LambdaQueryWrapper<Clazz>().eq(Clazz::getName, dto.getName()));
        if(count != null && count>0){
            throw new BusinessException(409,"班级已注册" + dto.getName());
        }

        Clazz clazz = Clazz.builder()
                .name(dto.getName())
                .grade(dto.getGrade())
                .teacherId(dto.getTeacherId())
                .build();
        try{
         clazzMapper.insert(clazz);
        }catch(DuplicateKeyException e){
            throw new BusinessException(409,"班级已注册" + dto.getName());
        }

    }

    @Override
    public ClazzResponse query(Long id) {
        Clazz clazz = clazzMapper.selectById(id);
        if(clazz == null){
            throw new BusinessException(404,"班级不存在");
        }
        return ClazzResponse.builder()
                .id(clazz.getId())
                .name(clazz.getName())
                .grade(clazz.getGrade())
                .teacherId(clazz.getTeacherId())
                .build();
    }

    @Override
    public PageResult<ClazzResponse> page(ClazzQueryDTO query) {
        String keyword = query.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String likeKeyword = hasKeyword ? "%" + keyword.trim() + "%" : null;

        Page<Clazz> page = clazzMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<Clazz>()
                        .like(hasKeyword, Clazz::getName, likeKeyword)
                        .orderByDesc(Clazz::getId));
        List<ClazzResponse> records = page.getRecords().stream().map(ClazzResponse::from).toList();
        return new PageResult<>(page.getTotal(),records,page.getCurrent(),page.getSize());
    }

    @Override
    public ClazzResponse update(Long id, ClazzUpdateRequestDTO dto) {

        Clazz clazz = clazzMapper.selectById(id);
        if (clazz == null) {
            throw new BusinessException(404, "班级不存在");
        }

        boolean hasChange = dto.getName() != null || dto.getTeacherId() != null || dto.getGrade() != null ;
        if (!hasChange) {
            throw new BusinessException(400, "至少提供一个要修改的字段");
        }

        // 3. 只有真的要改名字时才查重
        if (dto.getName() != null) {
            Long count = clazzMapper.selectCount(new LambdaQueryWrapper<Clazz>()
                    .eq(Clazz::getName, dto.getName())
                    .ne(Clazz::getId, id));        // ← 排除自己，id 是方法参数
            if (count != null && count > 0) {
                throw new BusinessException(409, "班级名已存在: " + dto.getName());
            }
        }

        // updateById 只 SET 非 null 字段（update-strategy 默认 NOT_NULL）
        Clazz newClazz = Clazz.builder()
                .id(id)
                .teacherId(dto.getTeacherId())
                .name(dto.getName())
                .grade(dto.getGrade())
                .build();
        clazzMapper.updateById(newClazz);

        // 返回更新后的完整数据，前端免二次查询
        return ClazzResponse.from(clazzMapper.selectById(id));
    }


    @Override
    public void delete(Long id) {
        Clazz clazz = clazzMapper.selectById(id);
        if(clazz == null){
            throw new BusinessException(404,"班级不存在");
        }
        // 删之前先看有没有学生挂在这个班下：班级被删而学生还在，学生会变成"无班可归"的孤儿数据。
        // 这里选择"拦住并提示"，而不是级联把学生一起删——删班级顺带删学生是灾难级误操作。
        Long studentCount = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getClazzId, id));
        if (studentCount != null && studentCount > 0) {
            throw new BusinessException(409, "该班级下还有 " + studentCount + " 名学生，请先调整学生所属班级");
        }
        clazzMapper.deleteById(id);
    }
}

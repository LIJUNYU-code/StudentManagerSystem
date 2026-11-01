package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.CourseQueryDTO;
import com.example.demo.dto.CourseRequestDTO;
import com.example.demo.dto.CourseUpdateRequestDTO;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.service.CourseService;
import com.example.demo.vo.CourseResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final EnrollmentMapper enrollmentMapper;


    @Override
    public CourseResponse query(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404,"无此课程");
        }
        return CourseResponse.from(course);
    }

    @Override
    @Transactional
    public CourseResponse add(CourseRequestDTO courseRequestDTO) {
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>().eq(Course::getName, courseRequestDTO.getName()));
        if(count != null && count>0){
            throw new BusinessException(409,"课程" + courseRequestDTO.getName() + "已经创建");
        }
        Course course = Course.builder()
                .name(courseRequestDTO.getName())
                .teacherId(courseRequestDTO.getTeacherId())
                .credit(courseRequestDTO.getCredit())
                .build();
        courseMapper.insert(course);

        return CourseResponse.from(course);

    }

    @Override
    public CourseResponse update(Long id, CourseUpdateRequestDTO courseUpdateRequestDTO) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404, "无此课程");
        }
        boolean hasChange = courseUpdateRequestDTO.getName() != null || courseUpdateRequestDTO.getTeacherId() != null || courseUpdateRequestDTO.getCredit() != null;
        if (!hasChange) {
            throw new BusinessException(400, "至少提供一个要修改的字段");
        }
        if (courseUpdateRequestDTO.getName() != null) {
            Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>()
                    .eq(Course::getName, courseUpdateRequestDTO.getName())
                    .ne(Course::getId, id));        // ← 排除自己，id 是方法参数
            if (count != null && count > 0) {
                throw new BusinessException(409, "课程已存在: " + courseUpdateRequestDTO.getName());
            }

        }
        Course newCourse = Course.builder()
                .id(id)
                .teacherId(courseUpdateRequestDTO.getTeacherId())
                .name(courseUpdateRequestDTO.getName())
                .credit(courseUpdateRequestDTO.getCredit())
                .build();
        courseMapper.updateById(newCourse);

        // 返回更新后的完整数据，前端免二次查询
        return CourseResponse.from(courseMapper.selectById(id));
    }


    @Override
    public PageResult<CourseResponse> page(CourseQueryDTO courseQueryDTO) {
        String keyword = courseQueryDTO.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String likeKeyword = hasKeyword ? "%" + keyword + "%" : null;

        Page<Course> page = courseMapper.selectPage(
                new Page<>(courseQueryDTO.getPage(), courseQueryDTO.getPageSize()),
                new LambdaQueryWrapper<Course>()
                        .like(hasKeyword, Course::getName, likeKeyword)
                        .orderByDesc(Course::getId));
        List<CourseResponse> records = page.getRecords().stream().map(CourseResponse::from).toList();
        return new PageResult<CourseResponse>(page.getTotal(),records,page.getCurrent(),page.getSize());

    }

    @Override
    public void delete(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException(404,"无此课程");
        }
        // 有选课记录就拦下：课程删了而选课/成绩还在，这些数据会指向不存在的课程。
        // 与班级删除同一策略——宁可让老师先处理关联数据，也不做级联删除。
        Long enrollmentCount = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getCourseId, id));
        if (enrollmentCount != null && enrollmentCount > 0) {
            throw new BusinessException(409, "该课程已有 " + enrollmentCount + " 条选课记录，请先清空选课再删除");
        }
        courseMapper.deleteById(id);
    }
}

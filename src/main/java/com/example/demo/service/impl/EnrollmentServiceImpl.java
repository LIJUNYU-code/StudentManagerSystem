package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.EnrollmentQueryDTO;
import com.example.demo.dto.EnrollmentRequestDTO;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.mapper.ScoreMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.service.EnrollmentService;
import com.example.demo.vo.EnrollmentResponse;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private EnrollmentMapper enrollmentMapper;
    private StudentMapper studentMapper;
    private CourseMapper courseMapper;
    private ScoreMapper scoreMapper;


    @Transactional
    @Override
    public EnrollmentResponse add(EnrollmentRequestDTO dto) {


        Student student = studentMapper.selectById(dto.getStudentId());
        if(student == null){
            throw new BusinessException(404,"学生不存在");
        }
        Course course = courseMapper.selectById(dto.getCourseId());
        if(course == null){
            throw new BusinessException(404,"课程不存在");
        }
        Long count = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, dto.getStudentId())
                .eq(Enrollment::getCourseId, dto.getCourseId()));

        if(count > 0){
            throw new BusinessException(409,"该学生已选择此课程");
        }
        Enrollment enrollment = new Enrollment();
        BeanUtils.copyProperties(dto,enrollment);
        enrollmentMapper.insert(enrollment);
        return EnrollmentResponse.from(enrollment, student.getName(), course.getName());
        }

    @Override
    public PageResult<EnrollmentResponse> page(EnrollmentQueryDTO user) {
        Long courseId = user.getCourseId();
        if (courseId == null) {
            // 缺课程 id 是"客户端没说清楚要查什么"，不是"资源不存在"：
            // 用 400 而不是 404——404 会让前端以为课程被删了。
            throw new BusinessException(400, "请指定课程 id");
        }
        Course course = courseMapper.selectById(courseId);
        if(course == null){
            throw new BusinessException(404,"课程不存在");
        }
        Page<Enrollment> page = enrollmentMapper.selectPage(new Page<Enrollment>(user.getPage(), user.getPageSize()), new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId).orderByDesc(Enrollment::getId));
        String courseName = course.getName();
        // ① 一次收集所有 studentId（distinct 去重）
        List<Long> ids = page.getRecords().stream().map(Enrollment::getStudentId).distinct().toList();

        if (ids.isEmpty()) {
            return new PageResult<>(page.getTotal(), List.of(), page.getCurrent(), page.getSize());
        }
        // ② 一次查回，转 Map<学生id, 姓名>
        Map<Long, String> nameMap = studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        // ③ 内存取值拼 VO（nameMap.get() 可能返回 null=学生被物理删过，VO 里显示 null 可接受）
        List<EnrollmentResponse> records = page.getRecords().stream().map(w -> EnrollmentResponse.from(w, nameMap.get(w.getStudentId()), courseName)).toList();
        return new PageResult<EnrollmentResponse>(page.getTotal(),records,page.getCurrent(),page.getSize());

    }

    @Transactional
    @Override
    public void delete(Long id) {
        Enrollment enrollment = enrollmentMapper.selectById(id);
        if (enrollment == null) throw new BusinessException(404, "选课记录不存在");
        Long scoreCount = scoreMapper.selectCount(
                new LambdaQueryWrapper<Score>().eq(Score::getEnrollmentId, id));
        if (scoreCount != null && scoreCount > 0) {
            throw new BusinessException(409, "该选课已有成绩，不能退课");
        }
        enrollmentMapper.deleteById(id);
    }

}





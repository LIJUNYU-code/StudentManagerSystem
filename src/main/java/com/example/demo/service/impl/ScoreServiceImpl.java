package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.PageResult;
import com.example.demo.dto.ScoreQueryDTO;
import com.example.demo.dto.ScoreRequestDTO;
import com.example.demo.entity.Enrollment;
import com.example.demo.entity.Score;
import com.example.demo.entity.Student;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.mapper.ScoreMapper;
import com.example.demo.mapper.StudentMapper;
import com.example.demo.service.ScoreService;
import com.example.demo.vo.ScoreResponse;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoreMapper scoreMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;

    @Override
    public void add(ScoreRequestDTO dto) {
        Long enrollmentId = dto.getEnrollmentId();
        if (enrollmentMapper.selectById(enrollmentId) == null) {
            throw new BusinessException(404, "选课记录不存在");
        }
        Long count = scoreMapper.selectCount(new LambdaQueryWrapper<Score>().eq(Score::getEnrollmentId, enrollmentId));

        if( count > 0){
            throw new BusinessException(409,"已填加学生成绩记录");
        }
        Score score = new Score();
        BeanUtils.copyProperties(dto,score);
        try {
            scoreMapper.insert(score);
        } catch (DuplicateKeyException e) {
            throw new DuplicateKeyException(e.getMessage());
        }
    }

    @Override
    public PageResult<ScoreResponse> page(ScoreQueryDTO dto) {
        // ① 先判课程存在（不存在直接 404，省掉后面所有查询）
        if (courseMapper.selectById(dto.getCourseId()) == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // ② 该课程的全部选课记录（score 表只认 enrollment_id，先拿到这批"准考证号"）
        List<Enrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, dto.getCourseId()));
        if (enrollments.isEmpty()) {
            // 课程存在但没人选课：直接返回空页。空列表不能丢给 in()（会生成非法 SQL）
            return new PageResult<>(0L, List.of(), dto.getPage(), dto.getPageSize());
        }
        List<Long> enrollmentIds = enrollments.stream().map(Enrollment::getId).toList();

        // ③ 分页查成绩：in() 一次圈定这批选课记录的所有成绩
        Page<Score> page = scoreMapper.selectPage(new Page<>(dto.getPage(), dto.getPageSize()),
                new LambdaQueryWrapper<Score>()
                        .in(Score::getEnrollmentId, enrollmentIds)
                        .orderByDesc(Score::getId));

        // ④ 两跳换名字：enrollmentId → studentId → 学生实体（全程内存 + 一次批量查，无 N+1）
        Map<Long, Long> enrollmentToStudent = enrollments.stream()
                .collect(Collectors.toMap(Enrollment::getId, Enrollment::getStudentId));
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(
                        enrollmentToStudent.values().stream().distinct().toList())
                .stream().collect(Collectors.toMap(Student::getId, s -> s));

        // ⑤ 拼 VO：score → enrollment 换 studentId → studentMap 换实体
        List<ScoreResponse> records = page.getRecords().stream()
                .map(w -> ScoreResponse.from(w, studentMap.get(enrollmentToStudent.get(w.getEnrollmentId()))))
                .toList();
        return new PageResult<>(page.getTotal(), records, page.getCurrent(), page.getSize());
    }

    @Override
    public List<ScoreResponse> query() {
        Long currentId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Student student = studentMapper.selectById(currentId);
        List<Enrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getStudentId, currentId));
        List<Long> ids = enrollments.stream().map(Enrollment::getId).toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .in(Score::getEnrollmentId, ids));
        if( scores == null || scores.isEmpty() ){
            return Collections.emptyList();
        }
        List<ScoreResponse> result = scores.stream().map(w -> ScoreResponse.from(w, student)).toList();
        return result;


    }
}

package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void add(ScoreRequestDTO dto) {
        Long enrollmentId = dto.getEnrollmentId();
        if (enrollmentMapper.selectById(enrollmentId) == null) {
            throw new BusinessException(404, "选课记录不存在");
        }
        // 同一选课记录、同一考试类型只能录一次；期中/期末/补考是三条独立记录
        // （数据库侧由 uk_score_enrollment_exam 兜底，这里先查是为了给出友好提示）
        Long count = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getEnrollmentId, enrollmentId)
                .eq(Score::getExamType, dto.getExamType()));
        if (count != null && count > 0) {
            throw new BusinessException(409, "该学生这门课的「" + dto.getExamType() + "」成绩已录入");
        }
        Score score = new Score();
        BeanUtils.copyProperties(dto, score);
        try {
            scoreMapper.insert(score);
        } catch (DuplicateKeyException e) {
            // 并发穿透：两个请求同时通过上面的查重，靠数据库唯一键兜底。
            // 翻译成业务异常（而不是把 DuplicateKeyException 原样抛出），
            // 否则会被全局兜底处理器当成 500，且丢掉"是重复录入"这层语义。
            throw new BusinessException(409, "该学生这门课的「" + dto.getExamType() + "」成绩已录入");
        }
    }

    @Override
    public PageResult<ScoreResponse> page(ScoreQueryDTO dto) {
        // ① 先判课程存在（不存在直接 404，省掉后面所有查询）
        if (courseMapper.selectById(dto.getCourseId()) == null) {
            throw new BusinessException(404, "课程不存在");
        }

        // ② 一条 join SQL 完成「成绩 + 学生」关联与分页。
        //    旧写法是先把整门课的选课记录全查出来再 in() 分页，
        //    课程人数一多就白搬几千行内存；现在由数据库直接返回当前页 10 行。
        long total = scoreMapper.countByCourse(dto.getCourseId());
        if (total == 0) {
            return new PageResult<>(0L, List.of(), dto.getPage(), dto.getPageSize());
        }
        long offset = (long) (dto.getPage() - 1) * dto.getPageSize();
        List<ScoreResponse> records =
                scoreMapper.selectPageByCourse(dto.getCourseId(), offset, dto.getPageSize());
        return new PageResult<>(total, records, dto.getPage(), dto.getPageSize());
    }

    @Override
    public List<ScoreResponse> query() {
        Long currentId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Student student = studentMapper.selectById(currentId);
        List<Enrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getStudentId, currentId));
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }
        // enrollmentId → 课程名（学生端要显示"哪门课"的成绩，光有学号没有意义）
        // 一次批量查课程再在内存里映射，避免在循环里逐条查库（N+1 查询）
        List<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId).distinct().toList();
        Map<Long, String> courseNameById = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        Map<Long, String> courseNameByEnrollment = enrollments.stream()
                .collect(Collectors.toMap(Enrollment::getId,
                        e -> courseNameById.get(e.getCourseId())));

        List<Long> ids = enrollments.stream().map(Enrollment::getId).toList();
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .in(Score::getEnrollmentId, ids)
                .orderByDesc(Score::getId));
        if (scores.isEmpty()) {
            return Collections.emptyList();
        }
        return scores.stream().map(w -> {
            ScoreResponse vo = ScoreResponse.from(w, student);
            vo.setCourseName(courseNameByEnrollment.get(w.getEnrollmentId()));
            return vo;
        }).toList();
    }
}

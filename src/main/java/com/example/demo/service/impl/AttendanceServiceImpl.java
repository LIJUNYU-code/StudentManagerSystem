package com.example.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.AttendanceBatchRequestDTO;
import com.example.demo.dto.AttendanceQueryDTO;
import com.example.demo.dto.AttendanceUpdateDTO;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.Course;
import com.example.demo.entity.Enrollment;
import com.example.demo.exception.BusinessException;
import com.example.demo.mapper.AttendanceMapper;
import com.example.demo.mapper.CourseMapper;
import com.example.demo.mapper.EnrollmentMapper;
import com.example.demo.service.AttendanceService;
import com.example.demo.vo.AttendanceRecordVO;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;
import lombok.AllArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final CourseMapper courseMapper;

    /**
     * 批量点名：已存在则更新，不存在则插入。
     *
     * <h3>为什么必须 @Transactional</h3>
     * 一次点名写 30 条记录。中途任何一条失败（比如某个学生 id 不存在），
     * 没有事务就会留下"前 15 个有记录、后 15 个没有"的脏状态，
     * 老师看到的名单一半有状态一半空白，且无法判断到底点没点。
     * 有事务则整体回滚，要么全成功要么全不生效。
     *
     * <h3>为什么不用 ON DUPLICATE KEY UPDATE</h3>
     * 那种写法要把 30 条拼成一条大 SQL，可读性差、出错时定位困难，
     * 而且拿不到"哪几条是新增、哪几条是更新"。30 条以内"先查后改"完全够用，
     * 单条循环的性能差异在这个量级可以忽略——不要过早优化。
     */
    @Override
    @Transactional
    public int batchSave(AttendanceBatchRequestDTO dto) {
        Long courseId = dto.getCourseId();
        requireCourse(courseId);

        // ① 校验「这些学生确实选了这门课」——这是"考勤不挂 enrollment"的代价，
        //    也是它唯一的补偿点：录入时检查一次，而不是永久耦合。
        //    一次查出该课程的全部选课学生 id，避免在循环里逐条查库（N+1）。
        Set<Long> enrolledStudentIds = enrollmentMapper.selectList(
                        new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getCourseId, courseId))
                .stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toSet());

        Set<Long> seen = new HashSet<>();
        for (AttendanceBatchRequestDTO.Item item : dto.getItems()) {
            if (!enrolledStudentIds.contains(item.getStudentId())) {
                throw new BusinessException(400,
                        "学生 " + item.getStudentId() + " 未选修该课程，无法点名");
            }
            // 同一请求里同一个学生出现两次 = 客户端 bug，直接拒绝而不是"后一条覆盖前一条"
            if (!seen.add(item.getStudentId())) {
                throw new BusinessException(400,
                        "请求中存在重复的学生 id：" + item.getStudentId());
            }
        }

        // ② 一次查出这些学生本节课的已有记录，避免逐条 selectOne
        List<Attendance> existing = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getCourseId, courseId)
                .eq(Attendance::getAttendDate, dto.getAttendDate())
                .eq(Attendance::getSessionNo, dto.getSessionNo())
                .in(Attendance::getStudentId, seen));

        Map<Long, Attendance> existingByStudent = existing.stream()
                .collect(Collectors.toMap(Attendance::getStudentId, a -> a, (a, b) -> a));

        // ③ 有则更新，无则插入
        int affected = 0;
        for (AttendanceBatchRequestDTO.Item item : dto.getItems()) {
            Attendance record = existingByStudent.get(item.getStudentId());
            if (record == null) {
                Attendance insert = Attendance.builder()
                        .studentId(item.getStudentId())
                        .courseId(courseId)
                        .attendDate(dto.getAttendDate())
                        .sessionNo(dto.getSessionNo())
                        .status(item.getStatus())
                        .remark(item.getRemark())
                        .build();
                affected += attendanceMapper.insert(insert);
            } else {
                Attendance patch = Attendance.builder()
                        .id(record.getId())
                        .status(item.getStatus())
                        .remark(item.getRemark())
                        .build();
                affected += attendanceMapper.updateById(patch);
            }
        }
        return affected;
    }

    @Override
    public List<AttendanceStudentVO> roster(AttendanceQueryDTO dto) {
        requireCourse(dto.getCourseId());
        return attendanceMapper.selectRoster(dto.getCourseId(), dto.getAttendDate(), dto.getSessionNo());
    }

    @Override
    public void update(Long id, AttendanceUpdateDTO dto) {
        Attendance existing = attendanceMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "考勤记录不存在");
        }
        Attendance patch = Attendance.builder()
                .id(id)
                .status(dto.getStatus())
                .remark(dto.getRemark())
                .build();
        attendanceMapper.updateById(patch);
    }

    @Override
    public List<AttendanceRecordVO> myAttendance() {
        Long currentId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<Attendance> records = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getStudentId, currentId)
                        .orderByDesc(Attendance::getAttendDate)
                        .orderByDesc(Attendance::getSessionNo));
        if (records.isEmpty()) {
            return List.of();
        }

        // 一次批量查课程名，避免循环里逐条查库（N+1）
        List<Long> courseIds = records.stream().map(Attendance::getCourseId).distinct().toList();
        // 用 HashMap 手动 put 而不是 Collectors.toMap：后者对 null value 会抛 NPE
        // （课程被逻辑删除时 selectBatchIds 查不到，这里就是 null）。这个坑在 ScoreServiceImpl 里踩过。
        Map<Long, String> courseNameById = new java.util.HashMap<>();
        for (Course c : courseMapper.selectBatchIds(courseIds)) {
            courseNameById.put(c.getId(), c.getName());
        }

        return records.stream().map(a -> AttendanceRecordVO.builder()
                .id(a.getId())
                .courseId(a.getCourseId())
                .courseName(courseNameById.get(a.getCourseId()))
                .attendDate(a.getAttendDate())
                .sessionNo(a.getSessionNo())
                .status(a.getStatus())
                .remark(a.getRemark())
                .createTime(a.getCreateTime())
                .build()).toList();
    }

    @Override
    public List<AttendanceStatVO> stats(Long courseId) {
        requireCourse(courseId);
        return attendanceMapper.selectStatsByCourse(courseId);
    }

    private void requireCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(400, "课程 id 不能为空");
        }
        if (courseMapper.selectById(courseId) == null) {
            throw new BusinessException(404, "课程不存在");
        }
    }
}

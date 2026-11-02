package com.example.demo.service;

import com.example.demo.dto.AttendanceBatchRequestDTO;
import com.example.demo.dto.AttendanceQueryDTO;
import com.example.demo.dto.AttendanceUpdateDTO;
import com.example.demo.vo.AttendanceRecordVO;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;

import java.util.List;

/**
 * 考勤管理。
 */
public interface AttendanceService {

    /**
     * 批量点名（核心接口）。
     * <p>
     * 语义是<b>覆盖</b>而不是"新增"：已存在的记录会被更新，不存在的才插入。
     * 老师点错了重新点一次不应该报 409。
     *
     * @return 实际写入（新增 + 更新）的记录数
     */
    int batchSave(AttendanceBatchRequestDTO dto);

    /** 某课程某天某节次的点名名单（含未点名的学生）。 */
    List<AttendanceStudentVO> roster(AttendanceQueryDTO dto);

    /** 修改单条考勤（补录/纠错）。 */
    void update(Long id, AttendanceUpdateDTO dto);

    /** 当前登录学生的考勤明细。身份从 token 取。 */
    List<AttendanceRecordVO> myAttendance();

    /** 某课程的出勤统计（按学生聚合）。 */
    List<AttendanceStatVO> stats(Long courseId);
}

package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Attendance;
import com.example.demo.vo.AttendanceStatVO;
import com.example.demo.vo.AttendanceStudentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    /**
     * 某课程某天某节次的点名名单：返回「该课程所有在读学生」+ 他们的考勤状态。
     * <p>
     * 为什么用 LEFT JOIN 而不是 JOIN：点名页面要列出<b>全班所有人</b>，
     * 包括还没被点到的（考勤记录为 null，前端显示"未点名"）。
     * 用 INNER JOIN 会导致"只显示已点名的人"，老师根本没法给新人点名。
     * <p>
     * 名单来源是 enrollment（当前选这门课的学生），不是 attendance。
     * 所以退课的学生不会出现在点名名单里，但他<b>历史</b>的考勤记录仍在表里——
     * 这正是"考勤与 enrollment 解耦"要达到的效果。
     */
    @Select("""
            SELECT st.id                AS studentId,
                   st.student_no        AS studentNo,
                   st.name              AS studentName,
                   a.id                 AS attendanceId,
                   a.status             AS status,
                   a.remark             AS remark
            FROM enrollment e
            JOIN student st ON e.student_id = st.id AND st.deleted = 0
            LEFT JOIN attendance a ON a.student_id = st.id
                                  AND a.course_id = e.course_id
                                  AND a.attend_date = #{attendDate}
                                  AND a.session_no = #{sessionNo}
            WHERE e.course_id = #{courseId}
            ORDER BY st.student_no
            """)
    List<AttendanceStudentVO> selectRoster(@Param("courseId") Long courseId,
                                           @Param("attendDate") LocalDate attendDate,
                                           @Param("sessionNo") Integer sessionNo);

    /**
     * 出勤率统计（按学生聚合，供图表和列表使用）。
     * <p>
     * total = 该学生这门课被点名的总次数；present = 状态为 0（出勤）的次数。
     * 注意这里 present 只算"出勤"，迟到/早退不算全勤——业务上可以争论，
     * 但先按严格口径实现，并在前端把 5 种状态都列出来，让使用者自己判断。
     * <p>
     * 用 COUNT(a.id) 而不是 COUNT(*)：LEFT JOIN 下没有考勤记录时 a.id 为 NULL，
     * COUNT(a.id) 返回 0，COUNT(*) 会返回 1（那行是 student 撑起来的）——
     * 这是个非常容易踩的坑，会让"从没被点名的学生"出勤率算成 100%。
     */
    @Select("""
            SELECT st.id                                                  AS studentId,
                   st.student_no                                          AS studentNo,
                   st.name                                                AS studentName,
                   COUNT(a.id)                                            AS total,
                   IFNULL(SUM(CASE WHEN a.status = 0 THEN 1 ELSE 0 END),0) AS present,
                   IFNULL(SUM(CASE WHEN a.status = 1 THEN 1 ELSE 0 END),0) AS late,
                   IFNULL(SUM(CASE WHEN a.status = 2 THEN 1 ELSE 0 END),0) AS earlyLeave,
                   IFNULL(SUM(CASE WHEN a.status = 3 THEN 1 ELSE 0 END),0) AS leaveCount,
                   IFNULL(SUM(CASE WHEN a.status = 4 THEN 1 ELSE 0 END),0) AS absent,
                   IFNULL(ROUND(SUM(CASE WHEN a.status = 0 THEN 1 ELSE 0 END)
                                / NULLIF(COUNT(a.id), 0), 4), 0)           AS attendanceRate
            FROM enrollment e
            JOIN student st ON e.student_id = st.id AND st.deleted = 0
            LEFT JOIN attendance a ON a.student_id = st.id AND a.course_id = e.course_id
            WHERE e.course_id = #{courseId}
            GROUP BY st.id, st.student_no, st.name
            ORDER BY st.student_no
            """)
    List<AttendanceStatVO> selectStatsByCourse(@Param("courseId") Long courseId);
}

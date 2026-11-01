package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Score;
import com.example.demo.vo.ScoreResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 按课程分页查成绩（一条 SQL 在数据库里完成 join + 分页）。
     * <p>
     * 为什么不用「先查全部选课 id 再 in()」：那会把整门课的选课记录都读进内存，
     * 课程人数一多就是无谓的搬运；join + LIMIT 让数据库只返回当前页的 10 行。
     * 注意：逻辑删除的过滤必须手写（自定义 SQL 不经过 MyBatis-Plus 的 @TableLogic 注入）。
     */
    @Select("""
            SELECT s.id, s.enrollment_id, s.value, s.exam_type, s.create_time, s.update_time,
                   st.name AS student_name, st.student_no
            FROM score s
            JOIN enrollment e ON s.enrollment_id = e.id
            JOIN student st ON e.student_id = st.id AND st.deleted = 0
            WHERE e.course_id = #{courseId} AND s.deleted = 0
            ORDER BY s.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ScoreResponse> selectPageByCourse(@Param("courseId") Long courseId,
                                           @Param("offset") long offset,
                                           @Param("size") long size);

    /** 与上面配套的总数查询（分页必须同源，否则页码会和内容对不上） */
    @Select("""
            SELECT COUNT(*)
            FROM score s
            JOIN enrollment e ON s.enrollment_id = e.id
            JOIN student st ON e.student_id = st.id AND st.deleted = 0
            WHERE e.course_id = #{courseId} AND s.deleted = 0
            """)
    long countByCourse(@Param("courseId") Long courseId);
}

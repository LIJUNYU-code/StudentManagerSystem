package com.example.demo.mapper;

import com.example.demo.vo.CourseAvgVO;
import com.example.demo.vo.CourseSummaryVO;
import com.example.demo.vo.ScoreBucketVO;
import com.example.demo.vo.StudentTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成绩统计专用 Mapper（聚合查询）。
 *
 * <h3>为什么单独建一个 Mapper，而不是塞进 ScoreMapper</h3>
 * ScoreMapper 的职责是「成绩的增删查」，统计是另一个关注点（读多写零、SQL 形态完全不同）。
 * 混在一起会让那个文件越来越难读；分开放，看名字就知道该去哪儿找。
 *
 * <h3>为什么统计必须放在数据库里算，而不是查出来在前端/Service 里算</h3>
 * 一个班 50 人 × 3 门课 × 2 次考试 = 300 条成绩，全查出来在内存里求平均只是几百行；
 * 但真实场景（多个班、多个学期）会迅速变成几万行。聚合交给数据库是 O(1) 传输，
 * 内存里算是 O(n) 传输。这是「数据量小时看不出差别，数据量大时天壤之别」的典型。
 *
 * <h3>⚠️ 自定义 SQL 必须手写 deleted = 0</h3>
 * MyBatis-Plus 的 @TableLogic 只在它自动生成的 SQL（selectList/selectById 等）里注入，
 * 手写 @Select 完全绕过这一层。漏写就会把「已删除的学生」和「已删除的成绩」算进统计，
 * 而且这种错误不会报错——只是数字悄悄变大。三个 join 里的 deleted 条件一个都不能少。
 */
@Mapper
public interface StatsMapper {

    /**
     * 课程成绩概览。
     * <p>
     * 用 IFNULL 兜底是刻意的：课程没人选、没人录成绩时，COUNT 返回 0 但 AVG/MAX/MIN
     * 返回 NULL。如果 VO 里是 BigDecimal 而拿到 NULL，前端 ECharts 会画不出图甚至报错。
     * 让接口永远返回「有数字」，是"空课程打开统计页不能 500"这条验收标准的实现方式。
     * <p>
     * passRate 的除零：COUNT(*)=0 时 SUM/COUNT 得到 NULL，再被 IFNULL 兜成 0，
     * 不会抛 division by zero（MySQL 里除零返回 NULL 而不是报错）。
     */
    @Select("""
            SELECT COUNT(*)                                                AS count,
                   IFNULL(ROUND(AVG(s.value), 1), 0)                       AS avg,
                   IFNULL(MAX(s.value), 0)                                 AS max,
                   IFNULL(MIN(s.value), 0)                                 AS min,
                   IFNULL(ROUND(SUM(CASE WHEN s.value >= 60 THEN 1 ELSE 0 END)
                                / COUNT(*), 4), 0)                         AS passRate
            FROM score s
            JOIN enrollment e ON s.enrollment_id = e.id
            JOIN student st   ON e.student_id = st.id AND st.deleted = 0
            WHERE e.course_id = #{courseId} AND s.deleted = 0
            """)
    CourseSummaryVO selectCourseSummary(@Param("courseId") Long courseId);

    /**
     * 分数段分布。只返回「有数据的段」，空段由 Service 层补齐成 0
     * ——否则柱状图会缺柱子，看起来像数据错乱。
     * <p>
     * ORDER BY MIN(s.value) 让 0-59 永远排在最左，而不是按字符串排序
     * （字符串排序会把 "0-59" 排到 "60-69" 前面纯属巧合，段名一改就错）。
     */
    @Select("""
            SELECT CASE
                     WHEN s.value < 60 THEN '0-59'
                     WHEN s.value < 70 THEN '60-69'
                     WHEN s.value < 80 THEN '70-79'
                     WHEN s.value < 90 THEN '80-89'
                     ELSE '90-100'
                   END        AS bucket,
                   COUNT(*)   AS count
            FROM score s
            JOIN enrollment e ON s.enrollment_id = e.id
            JOIN student st   ON e.student_id = st.id AND st.deleted = 0
            WHERE e.course_id = #{courseId} AND s.deleted = 0
            GROUP BY bucket
            ORDER BY MIN(s.value)
            """)
    List<ScoreBucketVO> selectDistributionByCourse(@Param("courseId") Long courseId);

    /**
     * 某班级各课程的平均分对比。
     * <p>
     * 注意 JOIN course 时也要 c.deleted = 0：课程被逻辑删除后，它的历史成绩
     * 仍会参与「班级对比」是没有意义的（那门课已经不存在了）。
     */
    @Select("""
            SELECT c.id                            AS courseId,
                   c.name                          AS courseName,
                   ROUND(AVG(s.value), 1)          AS avg,
                   COUNT(*)                        AS count
            FROM score s
            JOIN enrollment e ON s.enrollment_id = e.id
            JOIN student st   ON e.student_id = st.id AND st.deleted = 0
            JOIN course c     ON e.course_id = c.id AND c.deleted = 0
            WHERE st.clazz_id = #{clazzId} AND s.deleted = 0
            GROUP BY c.id, c.name
            ORDER BY c.id
            """)
    List<CourseAvgVO> selectCourseAvgByClazz(@Param("clazzId") Long clazzId);

    /**
     * 学生个人成绩明细（按课程 + 考试类型），供「成绩趋势」图表使用。
     * <p>
     * 为什么学生端也要单独做接口：现有 GET /scores/my 返回的 ScoreResponse 里有
     * enrollmentId 但没有课程 id，前端拿不到「哪门课」的稳定标识，无法按课程分组。
     * 这里直接把 courseName 和 examType 一起返回，前端零计算直接喂给图表。
     */
    @Select("""
            SELECT c.name       AS courseName,
                   s.exam_type  AS examType,
                   s.value      AS value
            FROM score s
            JOIN enrollment e ON s.enrollment_id = e.id
            JOIN course c     ON e.course_id = c.id AND c.deleted = 0
            WHERE e.student_id = #{studentId} AND s.deleted = 0
            ORDER BY c.id, s.exam_type
            """)
    List<StudentTrendVO> selectStudentTrend(@Param("studentId") Long studentId);
}

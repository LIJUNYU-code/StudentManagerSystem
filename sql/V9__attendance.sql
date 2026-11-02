-- ============================================================
-- V9__attendance.sql — 考勤表（阶段11）
--
-- 【最重要的设计决策：为什么不用 enrollment_id】
-- V6 明确把 enrollment 定为「当前状态表」而非「历史档案表」：退课是物理删除。
-- 如果 attendance 引用 enrollment_id，学生一退课，选课行消失，他的历史出勤
-- 记录立刻变成孤儿——查不到、算不出出勤率、统计全错。
-- 而考勤本质上就是历史档案，和「当前是否在读这门课」是两件事。
-- 所以这里直接存 student_id + course_id，与 enrollment 解耦：
--   - 退课不影响历史考勤
--   - 代价：录考勤时要额外校验「该学生确实选了这门课」，这是录入时的一次检查，
--     而不是永久耦合（Service 层负责）
--
-- 【为什么有 session_no】
-- 同一天可能有上午两节课、下午一节课。如果只按 (student_id, course_id, date)
-- 做唯一键，上午第二节就没法单独点名。默认值 1 让单节课场景完全不用关心它。
--
-- 前置条件：V1~V8 已执行。
-- 回滚：DROP TABLE attendance;
-- ============================================================

USE studentmanage;

CREATE TABLE IF NOT EXISTS attendance (
  id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id  INT          NOT NULL COMMENT '学生 student.id（逻辑关联，不引用 enrollment）',
  course_id   INT          NOT NULL COMMENT '课程 course.id（逻辑关联）',
  attend_date DATE         NOT NULL COMMENT '上课日期',
  session_no  TINYINT      NOT NULL DEFAULT 1 COMMENT '第几节课（同一天可多节）',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '出勤状态：0-出勤 1-迟到 2-早退 3-请假 4-旷课',
  remark      VARCHAR(200) NULL COMMENT '备注（请假原因等）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  -- 同一人、同一课、同一天、同一节只能有一条：防重复点名的最后一道防线。
  -- 业务层的"先查后改"可能被并发穿透，唯一键兜底（与 score 的 uk_score_enrollment_exam 同一思路）。
  UNIQUE KEY uk_attendance (student_id, course_id, attend_date, session_no),
  -- 点名页的主查询是「某课程某天的全班记录」，这个联合索引直接命中
  KEY idx_attendance_course_date (course_id, attend_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='考勤表（历史档案，与 enrollment 解耦）';

-- 验证（期望看到 attendance 表，且有两个索引：uk_attendance 和 idx_attendance_course_date）
SHOW TABLES;
SHOW INDEX FROM attendance;

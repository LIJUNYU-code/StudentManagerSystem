-- ============================================================
-- V12__leave_request.sql — 请假申请（阶段12）
--
-- 【为什么请假要单独建表，而不是直接改 attendance.status=3】
-- 因为「请假」和「出勤记录」是两件事，生命周期完全不同：
--   - 出勤记录是老师点名的结果（谁来了、谁没来），是既成事实
--   - 请假申请是学生的诉求（我下周要请假），需要审批才成立
-- 如果学生一提交就直接把考勤改成「请假」，等于学生自己给自己批假——
-- 那考勤统计就失去了意义。所以必须有一张表承载"申请"这个中间状态。
--
-- 【审批通过后怎么和考勤联动】
-- 由 Service 层在审批通过时把区间内对应课程的考勤写成 status=3（请假）。
-- 详见 LeaveServiceImpl.approve()。
--
-- 【状态机】只允许以下流转，Service 层校验，非法流转抛 400：
--   0 待审核 ──approve──> 1 已通过   （终态）
--   0 待审核 ──reject───> 2 已驳回   （终态）
--   0 待审核 ──cancel───> 3 已撤销   （终态，学生自己撤回）
--   1/2/3 都是终态，不能再变——否则"已通过的请假被驳回"这种数据会让考勤统计错乱。
--
-- 【为什么 course_id 是 NOT NULL】
-- 请假按课程走，不按"整个人"走。理由：
--   1. 只有按课程，审批通过后才知道要改哪门课的考勤
--   2. 学生可能只请某门课的假（上午有课下午没课）
-- 代价是请"全天假"要提交多条（一门课一条），但这个成本换来的是数据清晰。
--
-- 前置条件：V1~V11 已执行。
-- 回滚：DROP TABLE leave_request;
-- ============================================================

USE studentmanage;

CREATE TABLE IF NOT EXISTS leave_request (
  id            INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id    INT          NOT NULL COMMENT '申请学生 student.id（逻辑关联）',
  course_id     INT          NOT NULL COMMENT '请假课程 course.id（逻辑关联）',
  start_date    DATE         NOT NULL COMMENT '请假开始日期',
  end_date      DATE         NOT NULL COMMENT '请假结束日期（含当天）',
  reason        VARCHAR(300) NOT NULL COMMENT '请假理由',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-已通过 2-已驳回 3-已撤销',
  reviewer_id   INT          NULL COMMENT '审批人 user.id（逻辑关联）',
  review_remark VARCHAR(300) NULL COMMENT '审批意见',
  review_time   DATETIME     NULL COMMENT '审批时间',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  -- 学生的「我的请假」列表按时间倒序查，这个索引直接命中
  KEY idx_leave_student (student_id, create_time),
  -- 教师审批列表按状态筛选（待审核的排前面）
  KEY idx_leave_status (status, create_time),
  -- 审批通过时要查「这门课这个区间有没有别的请假」，按课程+日期查
  KEY idx_leave_course_date (course_id, start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='请假申请表';

-- 验证（期望看到 leave_request 表和三个索引）
SHOW TABLES;
SHOW INDEX FROM leave_request;

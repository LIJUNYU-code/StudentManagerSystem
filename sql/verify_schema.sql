-- ============================================================
-- verify_schema.sql — 只读 schema 校验（不修改任何数据）
--
-- 【为什么需要这个脚本】
-- 2026-11-02 出现过一次真实事故：代码已经用到 attendance / leave_request 表、
-- 以及 score.deleted 列，但当前 3306 库**落后了 5 个迁移脚本**，
-- 导致「成绩分析」「考勤」「请假」三个功能页全部 500。
-- 排查花了十几分钟才发现是"库没升级"，而不是代码有 bug。
--
-- 根本问题：项目用 V1~V13 编号脚本手工迁移，**没有任何机制记录哪些跑过**。
-- 每次升级只能靠人记住，一换机器、一换库就漂移。
--
-- 这个脚本的作用：把「代码期望的 schema」写成断言，一条命令查出来缺什么。
-- 每次拉新代码、换数据库、或怀疑环境不对时，先跑它。
--
-- 【怎么读结果】
-- 每个检查都返回一行，`ok` 列：
--   1 = 符合预期
--   0 = 缺失或不符合 → 需要执行对应迁移脚本
-- 全部为 1 才算环境与代码一致。
--
-- 【为什么用 information_schema 而不是直接 SHOW】
-- SHOW 需要人肉看输出、人肉比对；information_schema 可以写成 SQL 断言，
-- 结果只有 0/1，机器和人都不用"看"——这是能被自动化脚本消费的形式。
--
-- 【只读保证】
-- 本脚本只 SELECT information_schema 和 COUNT(*)，不含任何 INSERT/UPDATE/ALTER/DROP。
-- 可以随时在生产库上执行。
-- ============================================================

USE studentmanage;

SELECT '===== 1. 表是否存在 =====' AS section;

SELECT 'attendance 表 (V9)' AS item,
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='attendance') AS ok,
       '缺失 → 执行 V9__attendance.sql' AS fix_hint
UNION ALL
SELECT 'leave_request 表 (V12)',
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='leave_request'),
       '缺失 → 执行 V12__leave_request.sql'
UNION ALL
SELECT 'user 表 (V2)',
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='user'),
       '缺失 → 执行 V2__schema_refactor.sql'
UNION ALL
SELECT 'score 表 (V2)',
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='score'),
       '缺失 → 执行 V2__schema_refactor.sql'
UNION ALL
SELECT 'enrollment 表 (V2)',
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='enrollment'),
       '缺失 → 执行 V2__schema_refactor.sql'
UNION ALL
SELECT 'clazz 表 (V2)',
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='clazz'),
       '缺失 → 执行 V2__schema_refactor.sql'
UNION ALL
SELECT 'course 表 (V2)',
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='course'),
       '缺失 → 执行 V2__schema_refactor.sql';

SELECT '===== 2. 关键列是否存在 =====' AS section;

SELECT 'student.student_no (V2)' AS item,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='student' AND COLUMN_NAME='student_no') AS ok,
       '缺失 → 执行 V2__schema_refactor.sql' AS fix_hint
UNION ALL
SELECT 'student.clazz_id (V3)',
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='student' AND COLUMN_NAME='clazz_id'),
       '缺失 → 执行 V3__student_clazz_deleted.sql'
UNION ALL
SELECT 'student.deleted (V3)',
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='student' AND COLUMN_NAME='deleted'),
       '缺失 → 执行 V3__student_clazz_deleted.sql'
UNION ALL
SELECT 'score.deleted (V13) ⚠️ 曾导致统计 500',
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='score' AND COLUMN_NAME='deleted'),
       '缺失 → 执行 V13__score_deleted_restore.sql'
UNION ALL
SELECT 'user.pwd_change_time (V5)',
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='user' AND COLUMN_NAME='pwd_change_time'),
       '缺失 → 执行 V5__user_pwd_change_time.sql'
UNION ALL
SELECT 'attendance.session_no (V9)',
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='attendance' AND COLUMN_NAME='session_no'),
       '缺失 → 执行 V9__attendance.sql'
UNION ALL
SELECT 'leave_request.reviewer_id (V12)',
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='leave_request' AND COLUMN_NAME='reviewer_id'),
       '缺失 → 执行 V12__leave_request.sql';

SELECT '===== 3. 索引是否符合预期 =====' AS section;

SELECT 'score 联合唯一键 uk_score_enrollment_exam (V7)' AS item,
       (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='score'
          AND INDEX_NAME='uk_score_enrollment_exam') AS ok,
       '缺失 → 执行 V7__score_unique_exam_type.sql（否则一人一课只能录一条成绩）' AS fix_hint
UNION ALL
SELECT 'score 旧唯一键 uk_score_enrollment 已删除 (V7)',
       CASE WHEN (SELECT COUNT(*) FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='score'
                    AND INDEX_NAME='uk_score_enrollment') = 0 THEN 1 ELSE 0 END,
       '仍存在 → 执行 V7__score_unique_exam_type.sql'
UNION ALL
SELECT 'attendance 唯一键 uk_attendance (V9)',
       (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='attendance'
          AND INDEX_NAME='uk_attendance'),
       '缺失 → 执行 V9__attendance.sql'
UNION ALL
SELECT 'leave_request 索引 idx_leave_student (V12)',
       (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='leave_request'
          AND INDEX_NAME='idx_leave_student'),
       '缺失 → 执行 V12__leave_request.sql'
UNION ALL
SELECT 'student 学号唯一索引 (V2)',
       (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='student'
          AND COLUMN_NAME='student_no' AND NON_UNIQUE=0),
       '缺失 → 执行 V2__schema_refactor.sql';

SELECT '===== 4. 类型精度是否符合预期 =====' AS section;

-- pwd_change_time 必须是 DATETIME(3)：V8 的背景是「同一秒内登录又改密时旧 token 不失效」，
-- 秒级精度无法区分先后。这里 DATETIME_PRECISION=3 才算通过。
SELECT 'user.pwd_change_time 精度=3 (V8)' AS item,
       (SELECT CASE WHEN DATETIME_PRECISION = 3 THEN 1 ELSE 0 END
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='user'
          AND COLUMN_NAME='pwd_change_time') AS ok,
       '不是 3 → 执行 V8__pwd_change_time_millis.sql（否则改密后旧 token 可能仍有效）' AS fix_hint
UNION ALL
SELECT 'student.gender 是 tinyint (V2)',
       (SELECT CASE WHEN DATA_TYPE='tinyint' THEN 1 ELSE 0 END
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='student'
          AND COLUMN_NAME='gender'),
       '不是 tinyint → 执行 V2__schema_refactor.sql'
UNION ALL
SELECT 'score.value 是 decimal(5,1) (V2)',
       (SELECT CASE WHEN DATA_TYPE='decimal' AND NUMERIC_PRECISION=5 AND NUMERIC_SCALE=1
                    THEN 1 ELSE 0 END
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='score'
          AND COLUMN_NAME='value'),
       '不符合 → 检查 V2__schema_refactor.sql';

SELECT '===== 5. 数据健康检查（不影响 ok 列，供人工判断）=====' AS section;

-- 5.1 已删除但仍被统计 SQL 引用的孤儿成绩（deleted 列缺失时这条跑不了，先跳过）
SELECT 'score 表行数' AS item, COUNT(*) AS cnt FROM score
UNION ALL
SELECT 'score 中 exam_type 不在白名单的行数（V11 归零）',
       COUNT(*) FROM score WHERE exam_type NOT IN ('final','midterm','makeup')
UNION ALL
SELECT 'student 中 student_no 为空的存活行（应为 0）',
       COUNT(*) FROM student WHERE deleted = 0 AND (student_no IS NULL OR student_no = '')
UNION ALL
SELECT 'user 中 status 非法的行（应为 0）',
       COUNT(*) FROM `user` WHERE status NOT IN (0,1,2);

SELECT '===== 6. 汇总：还有几个不符预期 =====' AS section;

-- 汇总口径：把上面四类检查（缺表 / 缺列 / 缺索引 / 精度不符）各自算一遍求和。
-- total_mismatches = 0 表示环境与代码一致；> 0 时对照上面各节的 fix_hint 补脚本。
SELECT
  (
    -- 缺表
    (SELECT COUNT(*) FROM (
       SELECT 'attendance' AS t UNION ALL SELECT 'leave_request' UNION ALL
       SELECT 'user' UNION ALL SELECT 'score' UNION ALL
       SELECT 'enrollment' UNION ALL SELECT 'clazz' UNION ALL SELECT 'course'
     ) exp WHERE exp.t NOT IN (
       SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='studentmanage'
     ))
    +
    -- 缺关键列
    (SELECT COUNT(*) FROM (
       SELECT 'student' AS t, 'student_no' AS c UNION ALL
       SELECT 'student', 'clazz_id' UNION ALL
       SELECT 'student', 'deleted' UNION ALL
       SELECT 'score',   'deleted' UNION ALL
       SELECT 'user',    'pwd_change_time' UNION ALL
       SELECT 'attendance', 'session_no' UNION ALL
       SELECT 'leave_request', 'reviewer_id'
     ) exp WHERE NOT EXISTS (
       SELECT 1 FROM information_schema.COLUMNS col
       WHERE col.TABLE_SCHEMA='studentmanage'
         AND col.TABLE_NAME = exp.t AND col.COLUMN_NAME = exp.c
     ))
    +
    -- 缺索引
    (SELECT COUNT(*) FROM (
       SELECT 'score' AS t, 'uk_score_enrollment_exam' AS i UNION ALL
       SELECT 'attendance', 'uk_attendance' UNION ALL
       SELECT 'leave_request', 'idx_leave_student'
     ) exp WHERE NOT EXISTS (
       SELECT 1 FROM information_schema.STATISTICS st
       WHERE st.TABLE_SCHEMA='studentmanage'
         AND st.TABLE_NAME = exp.t AND st.INDEX_NAME = exp.i
     ))
    +
    -- 精度不符
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA='studentmanage' AND TABLE_NAME='user'
       AND COLUMN_NAME='pwd_change_time'
       AND (DATETIME_PRECISION IS NULL OR DATETIME_PRECISION <> 3))
  ) AS total_mismatches;

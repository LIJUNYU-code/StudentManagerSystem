-- ============================================================
-- V13__score_deleted_restore.sql — 补回 score.deleted 列
--
-- 【为什么需要这个脚本】
-- 2026-11-02 实测：成绩分析页全部接口返回 500。
-- 根因是 score 表**没有 deleted 列**，而 StatsMapper / ScoreMapper 的手写 SQL
-- 里写着 `s.deleted = 0`，MySQL 直接报：
--     ERROR 1054 (42S22): Unknown column 's.deleted' in 'where clause'
--
-- 【为什么之前的脚本补不上这一列——迁移脚本的经典陷阱】
-- V2__schema_refactor.sql 里 score 的建表语句**确实**写了 deleted 列，
-- 但用的是 `CREATE TABLE IF NOT EXISTS`：
--     - 表不存在 → 按语句建表，deleted 列会建出来
--     - 表已存在 → 整条语句被跳过，什么都不做
-- 也就是说，凡是在 V2 之前就已经手动建过 score 表的环境，这一列永远是缺的。
-- 「建表语句里的字段后来改过」对已存在的表完全无效，这是最容易被忽略的漂移来源。
--
-- 【为什么 score 应该保留 deleted（而不是像 enrollment 那样物理删除）】
-- 成绩是**历史档案**，和 enrollment（当前状态表）性质不同：
--   - 退课 → enrollment 行消失是合理的（"我现在不选这门课了"）
--   - 删成绩 → 必须是逻辑删除，否则学生的历史成绩记录会凭空消失，
--     而「谁在什么时候删了哪条成绩」这种审计需求完全无法满足
-- 所以 V6 对 enrollment 物理删除的决定，不能照搬到 score 上。
--
-- 【为什么列放 NOT NULL DEFAULT 0】
-- 与其余 4 张表（student/clazz/course/user）保持一致。
-- 若允许 NULL，历史行的 deleted 会是 NULL，
-- 而 `WHERE s.deleted = 0` 对 NULL 判定为 UNKNOWN → 那些行会被统计**静默漏掉**。
-- 这类"数字悄悄变小"的错误比报错更难发现，所以必须 NOT NULL。
--
-- 【可重复执行】
-- 先查 information_schema 判断列是否存在，不存在才 ALTER。
-- 重复执行只会走 IF 分支的 SELECT 提示，不会报错。
--
-- 前置条件：V2 已执行（score 表存在）。
-- 回滚：ALTER TABLE score DROP COLUMN deleted;（但会再次导致统计 500，不建议）
-- ============================================================

USE studentmanage;

-- 1. 判断列是否存在，不存在才加（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'studentmanage'
      AND TABLE_NAME   = 'score'
      AND COLUMN_NAME  = 'deleted'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE score ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0-正常 1-已删除'' AFTER exam_type',
    'SELECT ''score.deleted 已存在，跳过'' AS notice');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 给 deleted 建索引
-- 理由：所有统计 SQL 都带 `s.deleted = 0`，而这个条件的选择性极低（99% 的行都是 0），
-- 单独给 deleted 建索引没有意义。真正有用的是「课程 + deleted」这类组合索引，
-- 但 score 表要 join enrollment 才能拿到 course_id，无法在这里建。
-- 所以这里只确认索引现状，不新增——避免建一个用不上的索引反而拖慢写入。
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'studentmanage' AND TABLE_NAME = 'score'
GROUP BY INDEX_NAME, NON_UNIQUE;

-- 3. 验证：期望看到 deleted 列，类型 tinyint，NOT NULL，默认 0
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'studentmanage' AND TABLE_NAME = 'score'
ORDER BY ORDINAL_POSITION;

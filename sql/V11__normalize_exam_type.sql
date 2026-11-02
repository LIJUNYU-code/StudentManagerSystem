-- ============================================================
-- V11__normalize_exam_type.sql — 统一 exam_type 取值为英文代码
--
-- 【为什么需要这个脚本】
-- score.exam_type 是 VARCHAR(20)，建表时没有加约束，任何字符串都能写进去。
-- 结果库里同时存在两套值：
--   final / midterm        ← 种子数据和后来的正常录入
--   期末 / 期中 / 平时      ← 早期手工测试和前端旧版本写进去的
-- 前端按一套 key 做翻译（{ final: '期末' }），另一套就显示成英文原文或空白。
-- 这种数据不会让程序报错，只是"看起来有点怪"——正因如此会一直拖着不被发现。
--
-- 【为什么先归一化再校验，而不是直接改表结构】
-- 本项目的做法是"入口挡住新脏数据 + 迁移清理存量脏数据"：
--   入口：ScoreRequestDTO.examType 加了 @Pattern(final|midterm|makeup)
--   存量：就是这个脚本
-- 之所以不在数据库层加 CHECK 约束，是因为 MySQL 8 的 CHECK 约束在
-- 逻辑删除/历史数据场景下维护成本高，而且违反时报的是数据库异常（500），
-- 不如在 DTO 层用 @Pattern 得到 400 + 明确中文提示。
--
-- 【可重复执行】UPDATE 带 WHERE 条件，重复执行不会改变已归一的记录。
--
-- 前置条件：V1~V10 已执行。
-- ============================================================

USE studentmanage;

-- 先看一眼归一化前有哪些值（执行时把结果记下来，方便事后核对）
SELECT '归一化前' AS 阶段, exam_type, COUNT(*) AS 条数
FROM score
GROUP BY exam_type
ORDER BY 条数 DESC;

-- 中文 → 英文代码映射
-- 「平时」在业务上最接近 makeup（补考/平时分），归到 makeup 而不是硬造第四个值，
-- 因为 DTO 白名单只有三个值，多一个值会让前端翻译表和后端校验不一致。
UPDATE score SET exam_type = 'final'   WHERE exam_type IN ('期末', '期末考试');
UPDATE score SET exam_type = 'midterm' WHERE exam_type IN ('期中', '期中考试');
UPDATE score SET exam_type = 'makeup'  WHERE exam_type IN ('平时', '补考', '平时分');

-- 验证：归一化后应该只剩 final / midterm / makeup 三种
SELECT '归一化后' AS 阶段, exam_type, COUNT(*) AS 条数
FROM score
GROUP BY exam_type
ORDER BY 条数 DESC;

-- 如果有任何其他值残留，下面这条会查出来（期望 0 行）
SELECT '⚠️ 仍有未归一的取值' AS 警告, exam_type, COUNT(*) AS 条数
FROM score
WHERE exam_type NOT IN ('final', 'midterm', 'makeup')
GROUP BY exam_type;

-- 可选：给 exam_type 加索引（统计按类型分组时会用到）
-- 已存在 idx_score_exam_type 时这句会报错，属于正常现象，可跳过。
-- CREATE INDEX idx_score_exam_type ON score (exam_type);

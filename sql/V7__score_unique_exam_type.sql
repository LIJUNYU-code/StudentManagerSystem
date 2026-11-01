-- V7: 成绩唯一键从「一人一条」改为「一人一考试类型一条」
-- 背景（P0 缺陷）：V2 建的 uk_score_enrollment (enrollment_id) 让一条选课记录只能挂一条成绩，
--                  而业务上要录期中、期末、补考多次成绩，唯一键与业务语义直接矛盾。
-- 影响面：只放宽约束，不删除任何数据；已有成绩记录全部保留。
-- 回滚思路：若出现重复 (enrollment_id, exam_type) 数据，需先人工清理再重建唯一键。
USE studentmanage;

-- 1. 先确认没有重复数据（期望返回 0 行；有行则先人工处理再执行第 2 步）
SELECT enrollment_id, exam_type, COUNT(*) AS cnt
FROM score
WHERE deleted = 0
GROUP BY enrollment_id, exam_type
HAVING cnt > 1;

-- 2. 删除旧唯一键，建立「选课记录 + 考试类型」联合唯一键
ALTER TABLE score DROP INDEX uk_score_enrollment;
ALTER TABLE score ADD UNIQUE KEY uk_score_enrollment_exam (enrollment_id, exam_type);

-- 3. 验证（期望 uk_score_enrollment_exam 存在，uk_score_enrollment 已消失）
SHOW INDEX FROM score;

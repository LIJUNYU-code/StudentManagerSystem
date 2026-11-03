-- ============================================================
-- V10__seed_demo_data.sql — 演示用种子数据（阶段11）
--
-- 【为什么需要它】
-- 统计功能做完后，如果库里只有 3 个学生 2 条成绩，图表是空白的，演示效果为零。
-- 这个脚本造出「2 个班 / 20 人 / 3 门课 / 每人每课 2 次成绩」的规模，
-- 让分数段柱状图、班级对比图都有形状，同时也验证统计 SQL 在稍大数据量下是否正确。
--
-- 【可重复执行】
-- 全部使用 INSERT IGNORE / WHERE NOT EXISTS，重复执行不会报错、不会产生重复数据。
--
-- 【账号说明】
-- 20 个种子学生的登录密码统一为 Demo@2026
-- （复用了 V4 里 admin 的 BCrypt 哈希；BCrypt 是加盐哈希，同密码不同账号用同一哈希
--  在本项目这种演示数据场景下可接受。生产环境不应这样做。）
-- 学号：S202500101 ~ S202500120
--
-- 前置条件：V1~V9 已执行。
-- 清理：见文件末尾的注释掉的 DELETE 语句。
-- ============================================================

USE studentmanage;

-- ---------- 1. 班级（2 个，便于做「班级对比」图表）----------
INSERT IGNORE INTO clazz (name, grade) VALUES ('软件2301班', '2023级');
INSERT IGNORE INTO clazz (name, grade) VALUES ('软件2302班', '2023级');

-- ---------- 2. 学生（20 人，每班 10 人）----------
-- clazz_id 通过子查询取，避免依赖自增 id 的具体数值
INSERT IGNORE INTO student (student_no, clazz_id, name, gender, age, phone, password)
SELECT 'S202500101', c.id, '陈嘉明', 0, 19, '13800000101', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500102', c.id, '林小雨', 1, 19, '13800000102', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500103', c.id, '黄志强', 0, 20, '13800000103', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500104', c.id, '吴静怡', 1, 19, '13800000104', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500105', c.id, '周浩然', 0, 20, '13800000105', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500106', c.id, '郑雅琪', 1, 19, '13800000106', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500107', c.id, '孙博文', 0, 20, '13800000107', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500108', c.id, '马晓雯', 1, 19, '13800000108', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500109', c.id, '朱伟东', 0, 20, '13800000109', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500110', c.id, '胡佳怡', 1, 19, '13800000110', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2301班'
UNION ALL SELECT 'S202500111', c.id, '徐子豪', 0, 19, '13800000111', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500112', c.id, '何雨桐', 1, 20, '13800000112', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500113', c.id, '罗俊杰', 0, 19, '13800000113', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500114', c.id, '高欣妍', 1, 20, '13800000114', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500115', c.id, '梁泽楷', 0, 19, '13800000115', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500116', c.id, '谢若琳', 1, 20, '13800000116', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500117', c.id, '唐家豪', 0, 19, '13800000117', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500118', c.id, '曾诗涵', 1, 20, '13800000118', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500119', c.id, '邓凯文', 0, 19, '13800000119', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班'
UNION ALL SELECT 'S202500120', c.id, '许梦洁', 1, 20, '13800000120', '$2a$10$XV8AaxjOFQIn4FIsZs3ceeXqAQtXyOteqox9RBOW5B7z7Jx25OT8G' FROM clazz c WHERE c.name = '软件2302班';

-- ---------- 3. 课程（3 门）----------
INSERT IGNORE INTO course (name, credit) VALUES ('数据结构', 3.5);
INSERT IGNORE INTO course (name, credit) VALUES ('数据库原理', 3.0);
INSERT IGNORE INTO course (name, credit) VALUES ('计算机网络', 2.5);

-- ---------- 4. 选课（20 人 × 3 门 = 60 条）----------
-- 用 CROSS JOIN 让每个种子学生选中全部 3 门课；NOT EXISTS 保证可重复执行。
INSERT INTO enrollment (student_id, course_id)
SELECT st.id, co.id
FROM student st
CROSS JOIN course co
WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120'
  AND NOT EXISTS (
    SELECT 1 FROM enrollment e2
    WHERE e2.student_id = st.id AND e2.course_id = co.id
  );

-- ---------- 5. 成绩（每条选课 2 次：期中 + 期末，共 120 条）----------
-- 分值用确定性的伪随机公式生成，落在 40~100 区间：
--   value = 40 + ((student_id * 7 + course_id * 13 + 偏移) % 61)
-- 为什么不用 RAND()：RAND() 每次执行结果不同，脚本不可重复执行，
-- 也无法在测试里断言具体数值。确定性公式让「重跑脚本 = 同样数据」。
INSERT INTO score (enrollment_id, value, exam_type)
SELECT e.id,
       40 + MOD(e.student_id * 7 + e.course_id * 13, 61),
       'midterm'
FROM enrollment e
JOIN student st ON e.student_id = st.id
WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120'
  AND NOT EXISTS (
    SELECT 1 FROM score s2
    WHERE s2.enrollment_id = e.id AND s2.exam_type = 'midterm' AND s2.deleted = 0
  );

INSERT INTO score (enrollment_id, value, exam_type)
SELECT e.id,
       40 + MOD(e.student_id * 7 + e.course_id * 13 + 17, 61),
       'final'
FROM enrollment e
JOIN student st ON e.student_id = st.id
WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120'
  AND NOT EXISTS (
    SELECT 1 FROM score s2
    WHERE s2.enrollment_id = e.id AND s2.exam_type = 'final' AND s2.deleted = 0
  );

-- ---------- 6. 验证 ----------
-- 期望：20 名学生、2 个种子班、3 门种子课、60 条选课、120 条成绩
SELECT '学生' AS 对象, COUNT(*) AS 数量 FROM student WHERE student_no BETWEEN 'S202500101' AND 'S202500120'
UNION ALL SELECT '种子班级', COUNT(*) FROM clazz WHERE name IN ('软件2301班', '软件2302班')
UNION ALL SELECT '种子课程', COUNT(*) FROM course WHERE name IN ('数据结构', '数据库原理', '计算机网络')
UNION ALL SELECT '选课记录', COUNT(*) FROM enrollment e JOIN student st ON e.student_id = st.id
          WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120'
UNION ALL SELECT '成绩记录', COUNT(*) FROM score s JOIN enrollment e ON s.enrollment_id = e.id
          JOIN student st ON e.student_id = st.id
          WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120' AND s.deleted = 0;

-- 分数段分布预览（统计功能上线后应与此一致）
SELECT CASE
         WHEN s.value < 60 THEN '0-59'
         WHEN s.value < 70 THEN '60-69'
         WHEN s.value < 80 THEN '70-79'
         WHEN s.value < 90 THEN '80-89'
         ELSE '90-100'
       END AS 分数段,
       COUNT(*) AS 人数
FROM score s
JOIN enrollment e ON s.enrollment_id = e.id
JOIN student st ON e.student_id = st.id AND st.deleted = 0
WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120' AND s.deleted = 0
GROUP BY 分数段
ORDER BY MIN(s.value);

-- ---------- 清理（需要时手工执行）----------
-- DELETE s FROM score s JOIN enrollment e ON s.enrollment_id = e.id
--   JOIN student st ON e.student_id = st.id WHERE st.student_no BETWEEN 'S202500101' AND 'S202500120';
-- DELETE FROM enrollment WHERE student_id IN (SELECT id FROM student WHERE student_no BETWEEN 'S202500101' AND 'S202500120');
-- DELETE FROM student WHERE student_no BETWEEN 'S202500101' AND 'S202500120';
-- DELETE FROM course WHERE name IN ('数据结构', '数据库原理', '计算机网络');
-- DELETE FROM clazz WHERE name IN ('软件2301班', '软件2302班');

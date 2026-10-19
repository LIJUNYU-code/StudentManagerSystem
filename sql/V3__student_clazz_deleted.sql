-- ============================================================
-- V3__student_clazz_deleted.sql — student 表补充班级关联与逻辑删除
-- 前置条件：V2 已执行。
-- ============================================================

USE studentmanage;

ALTER TABLE student
    ADD COLUMN clazz_id INT  NULL COMMENT '班级 clazz.id（逻辑关联，不建物理外键）' AFTER student_no,
    ADD COLUMN deleted  TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除';

ALTER TABLE student
    ADD INDEX idx_student_clazz (clazz_id);

DESCRIBE student;

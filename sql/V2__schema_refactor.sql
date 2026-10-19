-- ============================================================
-- V2__schema_refactor.sql — 阶段1 数据库重构（在 V1 基础上升级）
--
-- 前置条件：V1 已执行过（student 表存在）。
-- 设计决策（详见 docs/project-planning/技术方案与数据库设计.md）：
--   1. gender char(1) → tinyint：与 Java Integer 实体类型对齐，消除隐式转换
--   2. name 加普通索引（不是唯一索引！）：业务允许重名，索引只为查询加速
--   3. 新增 student_no 学号列 + 唯一索引：真正代表“学生身份”的唯一键，
--      替代手机号唯一约束（手机号可能换、可能家长代填）
--   4. phone 唯一索引降级为普通索引：允许家庭共用手机号等场景
--   5. 时间列加默认值 CURRENT_TIMESTAMP，实体不再手动 set
--   6. 所有新表带 create_time/update_time/deleted（逻辑删除）
--   7. 不建物理外键：与逻辑删除配合容易出问题（班级被“删除”后历史数据
--      依然要能查出学生），完整性由 Service 层校验保证
-- ============================================================

USE studentmanage;

-- ---------- 1. student 表改造 ----------
-- 1a. gender: char(1) → tinyint。已存在数据 '0'/'1' 会隐式转换为 0/1；
--     NULL 保持 NULL。若存在其他字符会转换失败，执行前需检查：
--     SELECT id, gender FROM student WHERE gender IS NOT NULL AND gender NOT IN ('0','1');
ALTER TABLE student
    MODIFY COLUMN gender TINYINT NULL COMMENT '性别：0-男 1-女 NULL-未填写';

-- 1b. 新增学号列。已有 2 条测试数据，先加列再补号：
ALTER TABLE student
    ADD COLUMN student_no CHAR(10) NULL COMMENT '学号（唯一）' AFTER id;

-- 1c. 已有数据补学号（S + id 左补零，例如 S000000001）。
--     新数据由应用生成，此处只兜底历史数据。
UPDATE student SET student_no = CONCAT('S', LPAD(id, 9, '0')) WHERE student_no IS NULL;

-- 1d. 学号 NOT NULL + 唯一索引（唯一键加在“身份”上）
ALTER TABLE student
    MODIFY COLUMN student_no CHAR(10) NOT NULL COMMENT '学号（唯一）';
ALTER TABLE student
    ADD UNIQUE KEY uk_student_no (student_no);

-- 1e. name 普通索引（重名允许，仅加速查询）；phone 降级为普通索引
ALTER TABLE student
    ADD INDEX idx_student_name (name),
    DROP INDEX phone,
    ADD INDEX idx_student_phone (phone);

-- 1f. 时间列默认值（配合阶段2的 MyBatis-Plus 自动填充/DB 默认值）
ALTER TABLE student
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- ---------- 2. 班级表 ----------
CREATE TABLE IF NOT EXISTS clazz (
  id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(100) NOT NULL COMMENT '班级名，如 软件2301班',
  grade       VARCHAR(9)   NULL COMMENT '年级，如 2023级',
  teacher_id  INT          NULL COMMENT '班主任 user.id（逻辑关联，不建物理外键）',
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_clazz_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='班级表';

-- ---------- 3. 课程表 ----------
CREATE TABLE IF NOT EXISTS course (
  id          INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(100)  NOT NULL COMMENT '课程名',
  teacher_id  INT           NULL COMMENT '授课教师 user.id（逻辑关联）',
  credit      DECIMAL(3,1)  NULL COMMENT '学分，如 3.5',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='课程表';

-- ---------- 4. 用户表（教师/管理员登录用）----------
CREATE TABLE IF NOT EXISTS user (
  id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)  NOT NULL COMMENT '登录名（唯一）',
  password    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt 哈希）',
  real_name   VARCHAR(100) NOT NULL COMMENT '真实姓名',
  role        VARCHAR(20)  NOT NULL DEFAULT 'teacher' COMMENT '角色：admin-管理员 teacher-教师',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-正常 2-禁用',
  deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表（教师/管理员）';

-- ---------- 5. 选课表 ----------
CREATE TABLE IF NOT EXISTS enrollment (
  id         INT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  student_id INT      NOT NULL COMMENT '学生 id（逻辑关联）',
  course_id  INT      NOT NULL COMMENT '课程 id（逻辑关联）',
  deleted    TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除（退课时置1，保留历史）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment (student_id, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='选课表';

-- ---------- 6. 成绩表 ----------
CREATE TABLE IF NOT EXISTS score (
  id            INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  enrollment_id INT          NOT NULL COMMENT '选课记录 id（逻辑关联，唯一）',
  value         DECIMAL(5,1) NOT NULL COMMENT '成绩分值 0-100',
  exam_type     VARCHAR(20)  NOT NULL DEFAULT 'final' COMMENT '考试类型：final-期末 midterm-期中 makeup-补考',
  deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_score_enrollment (enrollment_id),
  INDEX idx_score_exam_type (exam_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='成绩表';

-- ---------- 7. 验证 ----------
SHOW TABLES;
-- 期望输出 6 张表：student, clazz, course, user, enrollment, score
-- 再抽验 student 改造结果：
DESCRIBE student;
SHOW INDEX FROM student;

-- ============================================================
-- V1__init.sql — 原始 student 表（历史结构存档，供全新环境从零重建）
-- 说明：这是项目最初的表结构。V2 会在它之上执行重构。
--       全新环境按顺序执行 V1 → V2 即可得到当前最新结构。
-- ============================================================

CREATE DATABASE IF NOT EXISTS studentmanage
  DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;

USE studentmanage;

CREATE TABLE IF NOT EXISTS student (
  id          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(100) NOT NULL                COMMENT '姓名（历史遗留：无唯一索引）',
  age         TINYINT      NULL                    COMMENT '年龄',
  gender      CHAR(1)      NULL                    COMMENT '性别（历史遗留：char(1)，0-男 1-女）',
  phone       CHAR(11)     NOT NULL                COMMENT '手机号（唯一）',
  create_time DATETIME     NULL                    COMMENT '创建时间',
  update_time DATETIME     NULL                    COMMENT '更新时间',
  password    VARCHAR(255) NOT NULL                COMMENT '密码（BCrypt 哈希）',
  PRIMARY KEY (id),
  UNIQUE KEY phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学生表';

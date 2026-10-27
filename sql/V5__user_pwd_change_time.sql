-- V5: 改密强制下线（阶段8）
-- 用户表增加 pwd_change_time：记录最近一次改密时间。
-- 过滤器验证 token 时比较 claims.iat（token 签发时间）与该列：
--   iat 早于 pwd_change_time 的 token 视为可疑，一律 401。
-- 存量数据迁移：现有用户视为"建号即改密"，用 create_time 兜底，
-- 使历史 token 的 iat（>= create_time 之后签发）不受影响。
ALTER TABLE `user`
    ADD COLUMN `pwd_change_time` DATETIME NULL DEFAULT NULL COMMENT '最近一次改密时间；NULL=从未改密' AFTER `status`;

UPDATE `user` SET `pwd_change_time` = `create_time` WHERE `pwd_change_time` IS NULL;

-- ============================================================
-- V8：改密划线时间提升到毫秒精度
-- 背景：JWT 标准 iat 只精确到秒，MySQL datetime 默认也只到秒。
--       若用户在"同一秒内"登录后又改密，旧 token 的 iat 与新划线时间相等，
--       比较结果变成"未过期"，旧 token 仍可继续使用——改密下线被绕过。
-- 做法：列改成 DATETIME(3)（毫秒），token 里额外带一个毫秒级 claim，
--       两者都用毫秒比较，同一秒内的先后也能区分。
-- 影响：仅精度变化，不改变已有数据语义（旧值毫秒位补 .000）。
-- ============================================================

USE studentmanage;

ALTER TABLE `user`
  MODIFY COLUMN `pwd_change_time` DATETIME(3) NULL COMMENT '最近一次改密时间（毫秒精度，用于旧 token 划线）';

SELECT id, username, pwd_change_time FROM `user`;

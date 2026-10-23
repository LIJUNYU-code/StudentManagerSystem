-- V4__seed_admin.sql
-- 作用：创建内置管理员账号（注册入口只开放教师注册，管理员只能从这里来）
-- 账号：admin / Admin@123456（哈希由 spring-security-crypto BCryptPasswordEncoder 生成并验证）
-- 注意：此文件会提交进 git。密码哈希可以入库（BCrypt 不可逆），但生产环境请在首次部署后立即改密。
-- 踩坑记录：real_name 为中文时经 pwsh 管道传给 mysql.exe 会因编码转换变成超长字节序列，
--           触发 Data too long for column 'real_name'（列实际 varchar(100)，ASCII 值正常）。
--           执行含中文的 SQL 请用 mysql 客户端交互模式或 --default-character-set=utf8mb4 的文件导入。

INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`)
VALUES ('admin', '$2a$10$YXgSavvk7Exrqn2ZG4NsZemLeqOC8BsG6obJ1NjK/WZ.eC/YDCFma', 'Admin', 'admin', 1);

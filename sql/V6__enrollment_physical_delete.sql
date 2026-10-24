-- V6: 选课表改为物理删除方案
-- 背景：模块4b 决策——enrollment 是"当前状态"表而非"历史档案"表，
--       退课（deleteById）后记录消失，重选不受联合唯一索引阻挡。
--       V2 预建的 deleted 列成为死列，删除以保持库结构与代码一致。
USE studentmanage;
ALTER TABLE enrollment DROP COLUMN deleted;
-- 验证（期望输出不再包含 deleted 列）：
DESCRIBE enrollment;

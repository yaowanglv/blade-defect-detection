-- blade 数据库结构初始化（仅结构，不含数据）
-- 在 MySQL 中先创建空库，再按序号执行本目录 SQL：
--   CREATE DATABASE IF NOT EXISTS blade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE blade;
--   SOURCE 01_user_auth.sql;
--   SOURCE 02_detection.sql;
--   SOURCE 03_system_config.sql;
--   SOURCE 04_dataview.sql;
--   SOURCE 05_views.sql;
--
-- 说明：
-- 01 用户账号、角色、权限、token 黑名单
-- 02 图片/视频检测记录与检测日志
-- 03 系统配置表
-- 04 数据看板统计表
-- 05 数据看板视图（依赖 detect 表）

CREATE DATABASE IF NOT EXISTS `blade` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `blade`;

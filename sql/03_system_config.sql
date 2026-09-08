-- blade 数据库结构（仅结构，不含数据）
-- 分类：系统配置
-- 来源：本机 MySQL 库 blade 导出（列结构以当前库为准）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键名',
  `config_value` text COMMENT '配置值',
  `config_group` varchar(50) NOT NULL COMMENT '配置分组: detect / video / global / ai_model',
  `config_label` varchar(100) DEFAULT NULL COMMENT '配置项中文说明',
  `default_value` text COMMENT '默认值（用于重置）',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_group` (`config_key`,`config_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

SET FOREIGN_KEY_CHECKS = 1;

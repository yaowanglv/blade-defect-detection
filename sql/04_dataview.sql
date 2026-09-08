-- blade 数据库结构（仅结构，不含数据）
-- 分类：数据看板统计表
-- 来源：本机 MySQL 库 blade 导出

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `dataview_stats`;
CREATE TABLE `dataview_stats` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '统计ID',
  `stat_type` varchar(50) NOT NULL COMMENT '统计类型: tumor_type/user_prediction/user_confidence/daily',
  `stat_key` varchar(100) NOT NULL COMMENT '统计键',
  `stat_value` decimal(10,4) DEFAULT NULL COMMENT '统计值',
  `stat_count` int DEFAULT '0' COMMENT '计数',
  `stat_date` date DEFAULT NULL COMMENT '统计日期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_stat_type` (`stat_type`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据可视化统计表';

SET FOREIGN_KEY_CHECKS = 1;

-- blade 数据库结构（仅结构，不含数据）
-- 分类：数据看板视图
-- 来源：本机 MySQL 库 blade 导出

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP VIEW IF EXISTS `v_daily_stats`;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v_daily_stats` AS select 'daily' AS `stat_type`,cast(`detect`.`create_time` as date) AS `stat_date`,count(0) AS `stat_count` from `detect` where (`detect`.`detect_status` = 2) group by cast(`detect`.`create_time` as date);

DROP VIEW IF EXISTS `v_tumor_type_stats`;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v_tumor_type_stats` AS select 'defect_type' AS `stat_type`,(case when ((json_extract(`detect`.`detection_data`,'$.defect_type') is not null) and (json_unquote(json_extract(`detect`.`detection_data`,'$.defect_type')) <> '') and (json_unquote(json_extract(`detect`.`detection_data`,'$.defect_type')) <> 'null')) then json_unquote(json_extract(`detect`.`detection_data`,'$.defect_type')) when ((json_extract(`detect`.`detection_data`,'$.tumor_type') is not null) and (json_unquote(json_extract(`detect`.`detection_data`,'$.tumor_type')) <> '') and (json_unquote(json_extract(`detect`.`detection_data`,'$.tumor_type')) <> 'null')) then json_unquote(json_extract(`detect`.`detection_data`,'$.tumor_type')) when ((json_extract(`detect`.`detection_data`,'$.boxes[0].defect_type') is not null) and (json_unquote(json_extract(`detect`.`detection_data`,'$.boxes[0].defect_type')) <> '')) then json_unquote(json_extract(`detect`.`detection_data`,'$.boxes[0].defect_type')) when ((json_extract(`detect`.`detection_data`,'$.boxes[0].label') is not null) and (json_unquote(json_extract(`detect`.`detection_data`,'$.boxes[0].label')) <> '')) then json_unquote(json_extract(`detect`.`detection_data`,'$.boxes[0].label')) else 'normal' end) AS `stat_key`,count(0) AS `stat_count` from `detect` where (`detect`.`detect_status` = 2) group by `stat_key`;

DROP VIEW IF EXISTS `v_user_confidence_stats`;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v_user_confidence_stats` AS select 'user_confidence' AS `stat_type`,`detect`.`user_name` AS `stat_key`,avg(cast(json_unquote(json_extract(`detect`.`detection_data`,'$.boxes[0].confidence')) as decimal(5,4))) AS `stat_value`,count(0) AS `stat_count` from `detect` where ((`detect`.`detect_status` = 2) and (`detect`.`detection_data` is not null)) group by `detect`.`user_id`,`detect`.`user_name`;

DROP VIEW IF EXISTS `v_user_prediction_stats`;
CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v_user_prediction_stats` AS select 'user_prediction' AS `stat_type`,`detect`.`user_name` AS `stat_key`,count(0) AS `stat_count` from `detect` where (`detect`.`detect_status` = 2) group by `detect`.`user_id`,`detect`.`user_name`;

SET FOREIGN_KEY_CHECKS = 1;

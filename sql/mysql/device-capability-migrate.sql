-- 设备结果接收能力
SET @db = DATABASE();
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `device_info` ADD COLUMN `poll_enabled` bit NOT NULL DEFAULT b''0'' COMMENT ''是否支持平台轮询'' AFTER `callback_enabled`, ADD COLUMN `status_query_enabled` bit NOT NULL DEFAULT b''0'' COMMENT ''是否支持状态查询'' AFTER `poll_enabled`',
  'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='device_info' AND COLUMN_NAME='poll_enabled');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

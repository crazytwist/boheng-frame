-- 设备动作：请求报文格式（幂等迁移）
-- 已有库执行一次即可；全新库直接用 device.sql

SET NAMES utf8mb4;

-- body_format：报文格式
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_action' AND COLUMN_NAME = 'body_format'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `device_action` ADD COLUMN `body_format` varchar(16) NULL COMMENT ''请求报文格式: JSON/FORM 表单/TEXT 纯文本/XML'' AFTER `http_method`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

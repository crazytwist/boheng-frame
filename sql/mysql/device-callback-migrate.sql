-- =============================================================================
-- 设备指令回调：幂等迁移版本
-- 已有库执行一次即可；全新库直接用 device.sql
-- =============================================================================

SET NAMES utf8mb4;

-- device_info: callback_url + callback_timeout_sec
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='boheng-frame' AND TABLE_NAME='device_info' AND COLUMN_NAME='callback_url');
SET @ddl := IF(@col_exists=0,
    'ALTER TABLE `device_info` ADD COLUMN `callback_url` varchar(512) NULL COMMENT ''设备回调平台用的地址。空则用平台默认地址；可写 ${commandNo}、${callbackToken}、${deviceCode} 占位符'' AFTER `callback_enabled`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='boheng-frame' AND TABLE_NAME='device_info' AND COLUMN_NAME='callback_timeout_sec');
SET @ddl := IF(@col_exists=0,
    'ALTER TABLE `device_info` ADD COLUMN `callback_timeout_sec` int NULL COMMENT ''默认等回调的秒数(1-600)。动作上没填时用它，都没填按 300'' AFTER `callback_url`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- device_action: result_mode + callback_timeout_sec
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='boheng-frame' AND TABLE_NAME='device_action' AND COLUMN_NAME='result_mode');
SET @ddl := IF(@col_exists=0,
    'ALTER TABLE `device_action` ADD COLUMN `result_mode` varchar(16) NOT NULL DEFAULT ''SYNC_RETURN'' COMMENT ''结果怎么拿: SYNC_RETURN 直接看响应/CALLBACK 等设备回调'' AFTER `poll_max_times`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='boheng-frame' AND TABLE_NAME='device_action' AND COLUMN_NAME='callback_timeout_sec');
SET @ddl := IF(@col_exists=0,
    'ALTER TABLE `device_action` ADD COLUMN `callback_timeout_sec` int NULL COMMENT ''等回调的秒数(1-600)；为空用设备台账上的默认值'' AFTER `result_mode`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- device_simulate_profile: return_mode + async_wait_ms（MODIFY，幂等：查 column_default）
SET @col_default := (SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='boheng-frame' AND TABLE_NAME='device_simulate_profile' AND COLUMN_NAME='return_mode');
SET @ddl := IF(@col_default IS NULL OR @col_default != '''SYNC''',
    'ALTER TABLE `device_simulate_profile` MODIFY COLUMN `return_mode` varchar(16) NOT NULL DEFAULT ''SYNC'' COMMENT ''回收方式: SYNC 同步返回/CALLBACK 受理后由仿真设备回调/POLL 受理后由服务端轮询''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- UPDATE：ASYC→CALLBACK，幂等（重复跑 WHERE 不匹配就 0 行）
UPDATE `device_simulate_profile` SET `return_mode` = 'CALLBACK' WHERE `return_mode` = 'ASYNC';

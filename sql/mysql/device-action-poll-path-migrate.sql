-- 设备动作：增加平台轮询查询接口路径
SET @db = DATABASE();
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'device_action' AND COLUMN_NAME = 'poll_path'
        ),
        'SELECT 1',
        'ALTER TABLE `device_action` ADD COLUMN `poll_path` varchar(255) NULL COMMENT ''平台轮询查询接口相对路径'' AFTER `request_path`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

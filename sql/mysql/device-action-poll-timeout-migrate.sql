-- 将轮询的用户配置从“最大次数”升级为“最大等待时间”。
SET @db = DATABASE();
SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `device_action` ADD COLUMN `poll_timeout_sec` int NULL COMMENT ''平台轮询最大等待时间（秒），按设备轮询间隔换算次数'' AFTER `poll_done_expr`',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'device_action' AND COLUMN_NAME = 'poll_timeout_sec'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

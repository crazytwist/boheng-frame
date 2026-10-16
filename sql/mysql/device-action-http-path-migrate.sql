-- 设备动作补上 HTTP 方法与相对路径。
-- 主机仍在 device_info.endpoint_url；动作按设备类型共用，只记这条命令自己的路径。
-- 可重复执行：列已存在时跳过。

SET NAMES utf8mb4;

SET @db = DATABASE();

SET @exists_method = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'device_action' AND COLUMN_NAME = 'http_method'
);
SET @sql_method = IF(
    @exists_method = 0,
    'ALTER TABLE `device_action` ADD COLUMN `http_method` varchar(16) NULL COMMENT ''HTTP 方法: GET/POST/PUT/PATCH/DELETE。与台账 endpoint_url 拼接时使用；非 HTTP 接入可空'' AFTER `status_command_code`',
    'SELECT 1'
);
PREPARE stmt FROM @sql_method;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists_path = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'device_action' AND COLUMN_NAME = 'request_path'
);
SET @sql_path = IF(
    @exists_path = 0,
    'ALTER TABLE `device_action` ADD COLUMN `request_path` varchar(255) NULL COMMENT ''接口相对路径(如 /api/read)。主机在 device_info.endpoint_url，动作按设备类型共用，不写完整地址'' AFTER `http_method`',
    'SELECT 1'
);
PREPARE stmt FROM @sql_path;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

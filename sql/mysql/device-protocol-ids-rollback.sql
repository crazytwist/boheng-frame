-- =============================================================================
-- 回滚：移除 device_command.event_id / request_id
--
-- 背景：这两个字段一度被当作「厂商协议标识」做了特殊处理（流程侧确定性生成
-- EventId、落库两列、模板专用变量 ${eventId}/${requestId}）。实际上它们就是
-- 普通传参字段，值要求为不重复的 UUID，模板里用 ${uuid()} 现填即可，
-- 不需要平台参与，因此把列一并撤掉，避免留下没人读的空列。
--
-- 特性：幂等。列或索引不存在时什么也不做。
-- 执行：
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 \
--     -D boheng-frame < sql/mysql/device-protocol-ids-rollback.sql
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 先撤索引（列删掉索引会一起没，这里显式一步，便于排查）--------------------
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command'
      AND INDEX_NAME = 'idx_request_id'
);
SET @ddl := IF(@idx_exists > 0,
    'ALTER TABLE `device_command` DROP INDEX `idx_request_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 撤两列 -------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command' AND COLUMN_NAME = 'request_id'
);
SET @ddl := IF(@col_exists > 0,
    'ALTER TABLE `device_command` DROP COLUMN `request_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command' AND COLUMN_NAME = 'event_id'
);
SET @ddl := IF(@col_exists > 0,
    'ALTER TABLE `device_command` DROP COLUMN `event_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 校验：留存的列只剩 error_type / retryable / params_source -----------------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command'
  AND COLUMN_NAME IN ('event_id', 'request_id', 'error_type', 'retryable', 'params_source');

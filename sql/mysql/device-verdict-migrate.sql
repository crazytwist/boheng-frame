-- =============================================================================
-- 设备域：协议判定结果与参数来源落档（幂等迁移）
--
-- 背景：厂商的响应体用 ExecuteStatus / ResultCode 表达成败，
-- 平台原来在回调里只看 status / success 两个字段，失败回调会被判成成功。
-- 这里给动作加判定规则，给命令加判定结论与参数来源，供排障与流程重试使用。
--
-- 执行：
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 \
--     -D boheng-frame < sql/mysql/device-verdict-migrate.sql
-- =============================================================================

SET NAMES utf8mb4;

-- 1. device_action.result_rule：响应判定规则 --------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_action' AND COLUMN_NAME = 'result_rule'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `device_action` ADD COLUMN `result_rule` json NULL COMMENT ''响应判定规则: 成功判定字段路径与取值、错误分类字段、可重试性字段'' AFTER `poll_done_expr`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. device_command.error_type：失败分类 ------------------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command' AND COLUMN_NAME = 'error_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `device_command` ADD COLUMN `error_type` varchar(32) NULL COMMENT ''失败分类: TRANSPORT 传输/DEVICE_REJECTED 设备拒绝/BUSINESS 业务/TIMEOUT 超时'' AFTER `error_msg`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. device_command.retryable：设备声明的可重试性 ----------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command' AND COLUMN_NAME = 'retryable'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `device_command` ADD COLUMN `retryable` bit(1) NULL COMMENT ''设备声明的可重试性: 1 可重试/0 不可重试；NULL 表示未声明，由流程按默认处理'' AFTER `error_type`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. device_command.params_source：每个参数的取值来源 ------------------------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'device_command' AND COLUMN_NAME = 'params_source'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `device_command` ADD COLUMN `params_source` json NULL COMMENT ''参数取值来源: {参数名: INPUT 流程传入/SCHEMA_DEFAULT 契约默认}'' AFTER `params_json`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. 校验 ---------------------------------------------------------------------
--    协议标识（EventId / RequestId）按普通传参处理，由报文模板的 ${uuid()} 现填，
--    不落库、不做特殊处理，所以这里没有 event_id / request_id。
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'boheng-frame'
  AND ((TABLE_NAME = 'device_action' AND COLUMN_NAME = 'result_rule')
    OR (TABLE_NAME = 'device_command'
        AND COLUMN_NAME IN ('error_type', 'retryable', 'params_source')))
ORDER BY TABLE_NAME, COLUMN_NAME;

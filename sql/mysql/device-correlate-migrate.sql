-- ============================================================================
-- 固定地址回调：靠业务字段反查命令
--
-- 背景：海康 RCS 的回调地址在厂商侧写死，报文里既不给我们的命令号，
--       也没法把 `${callbackUrl}`（带一次性令牌）传进去 —— 它的请求报文
--       只有 4 个字段，没有放回调地址的地方。
--
-- 做法：我们发给它的 reqCode 是每次唯一的，让它原样带回来。
--       · device_action.correlate_path   配从请求报文取哪个字段当关联键（如 $.reqCode）
--       · device_command.correlate_value 渲染后把这个值单独存一列并建索引
--       · 回调进来时按 body 里的值反查 correlate_value，命中「还在等回调」的那条命令
--
-- 注意：这条路没有一次性令牌（厂商带不了），安全靠 correlate_value 的随机性
--       （16 位十六进制 = 64 bit）和「只匹配 WAITING 状态」两道限制。
--       带令牌的 /api/callback/{commandNo} 仍是首选，这里是补充。
--
-- 幂等：加列/加索引前先查 information_schema。
-- ============================================================================

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'device_action' AND column_name = 'correlate_path') = 0,
    'ALTER TABLE `device_action` ADD COLUMN `correlate_path` varchar(128) NULL COMMENT ''固定地址回调的关联键路径，如 $.reqCode；为空表示只支持带命令号的回调'' AFTER `result_rule`',
    'SELECT 1'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'device_command' AND column_name = 'correlate_value') = 0,
    'ALTER TABLE `device_command` ADD COLUMN `correlate_value` varchar(128) NULL COMMENT ''固定地址回调的关联键值，取自发出报文里 correlate_path 指向的字段'' AFTER `payload_json`',
    'SELECT 1'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'device_command' AND index_name = 'idx_correlate_value') = 0,
    'ALTER TABLE `device_command` ADD INDEX `idx_correlate_value` (`correlate_value`)',
    'SELECT 1'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- AGV：reqCode 就是关联键
-- ---------------------------------------------------------------------------
UPDATE device_action
SET correlate_path = '$.reqCode',
    updater        = 'yinan'
WHERE action_code = 'genAgvSchedulingTask'
  AND device_type_code = 'AGV'
  AND deleted = 0;

-- ---------------------------------------------------------------------------
-- HPLC：EventId 和 RequestId 都是我们现生成的，回调时回哪个都认
--   关联键列只存得下一个（按顺序取 EventId），RequestId 靠回调时的
--   报文内容兜底匹配补上。
-- ---------------------------------------------------------------------------
UPDATE device_action
SET correlate_path = '$.EventId,$.RequestId',
    updater        = 'yinan'
WHERE action_code = 'trun'
  AND device_type_code = 'MACHINE'
  AND deleted = 0;

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
SELECT action_code, result_mode, correlate_path, request_template
FROM device_action WHERE action_code IN ('genAgvSchedulingTask', 'trun');

SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'device_command' AND column_name = 'correlate_value';

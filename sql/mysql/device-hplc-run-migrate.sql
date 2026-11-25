-- =============================================================================
-- HPLC（S6000PLUS）托盘转动指令 —— 设备动作配置
--
-- 目标动作：MACHINE / trun（转动）
-- 报文来源：厂商给出的 Rotate 指令样例
--   EventId / RequestId 就是普通字段，值要求是不重复的 UUID，模板里用 ${uuid()} 现填；
--   每次出现都生成新值，所以两个字段天然不同，不需要平台额外处理
--   TrayNumber 是唯一的业务参数，由调用方（人工调试 / 流程映射）传入
--
-- 特性：幂等。动作已存在则只补模板与契约，不存在则建一条。
-- 执行：
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 \
--     -D boheng-frame < sql/mysql/device-hplc-run-migrate.sql
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 已存在则补全：模板 + 参数契约 + 报文元信息 -------------------------------
UPDATE `device_action`
SET `param_schema` = '{"TrayNumber":{"label":"托盘号","type":"INTEGER","required":true,"default":1,"constraints":{"min":1,"max":9}}}',
    `request_template` = '{"Command":"Rotate","CommandSyncAsync":0,"DeviceId":"S6000PLUS","EventId":"${uuid()}","Parameters":{"TrayNumber":${TrayNumber}},"RequestId":"${uuid()}","RunMode":0}',
    `result_rule` = '{"successStatusPath":"$.ExecuteStatus","successStatusValues":[0],"successCodePath":"$.ResultCode","successCodeValues":["00000000"],"messagePath":"$.Message","retryHintsPath":"$.Parameters.HandleCommands"}',
    `http_method` = 'POST',
    `body_format` = 'JSON',
    `vendor_ref` = 'S6000PLUS.Rotate',
    `estimate_duration_ms` = 10000,
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `device_type_code` = 'MACHINE'
  AND `action_code` = 'trun'
  AND `deleted` = b'0';

-- 2. 不存在则新建（种子 id 走 9101 段，避开已有的 910001-910005 动作种子）-------
INSERT INTO `device_action`
(`id`, `device_type_code`, `action_code`, `action_name`, `param_schema`, `request_template`, `result_rule`,
 `http_method`, `body_format`, `request_path`, `vendor_ref`, `source`, `estimate_duration_ms`, `result_mode`,
 `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 910101, 'MACHINE', 'trun', '转动',
       '{"TrayNumber":{"label":"托盘号","type":"INTEGER","required":true,"default":1,"constraints":{"min":1,"max":9}}}',
       '{"Command":"Rotate","CommandSyncAsync":0,"DeviceId":"S6000PLUS","EventId":"${uuid()}","Parameters":{"TrayNumber":${TrayNumber}},"RequestId":"${uuid()}","RunMode":0}',
       '{"successStatusPath":"$.ExecuteStatus","successStatusValues":[0],"successCodePath":"$.ResultCode","successCodeValues":["00000000"],"messagePath":"$.Message","retryHintsPath":"$.Parameters.HandleCommands"}',
       'POST', 'JSON', '/api/BusFrame/System/Post', 'S6000PLUS.Rotate', 'MANUAL', 10000, 'CALLBACK', 0,
       'admin', NOW(), '', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `id` FROM `device_action`
                   WHERE `device_type_code` = 'MACHINE' AND `action_code` = 'trun' AND `deleted` = b'0') AS existing
);

-- 3. 仿真配置：HPLC 是 SIMULATED 设备，没有这条就调不动 ----------------------
--    request_template 留空表示沿用动作上的模板，避免两处维护同一份报文。
--    回包按 7.1.2 成功应答的形状构造。注意仿真回包模板只能引用请求报文的顶层字段
--    （TrayNumber 在 Parameters 里取不到）。
UPDATE `device_simulate_profile`
SET `return_mode` = 'SYNC',
    `response_body` = '{"ExecuteStatus":0,"ResultCode":"00000000","Message":"成功","Command":"${Command}","Parameters":{},"Data":null}',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `device_id` = 2125143472289202177 AND `action_code` = 'trun' AND `deleted` = b'0';

INSERT INTO `device_simulate_profile`
(`id`, `device_id`, `device_code`, `action_code`, `return_mode`, `async_wait_ms`, `poll_interval_ms`,
 `poll_max_times`, `request_template`, `response_body`, `status`, `creator`, `create_time`, `updater`,
 `update_time`, `deleted`, `tenant_id`)
SELECT 910102, 2125143472289202177, 'HPLC', 'trun', 'SYNC', 0, 1000, 30, NULL,
       '{"ExecuteStatus":0,"ResultCode":"00000000","Message":"成功","Command":"${Command}","Parameters":{},"Data":null}', 0,
       'admin', NOW(), '', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `id` FROM `device_simulate_profile`
                   WHERE `device_id` = 2125143472289202177 AND `action_code` = 'trun'
                     AND `deleted` = b'0') AS existing
);

-- 4. 校验：模板必须与契约对得上 ------------------------------------------------
SELECT `id`, `action_code`, `action_name`, `http_method`, `body_format`, `request_path`,
       `param_schema`, `request_template`, `result_mode`, `status`
FROM `device_action`
WHERE `device_type_code` = 'MACHINE' AND `action_code` = 'trun' AND `deleted` = b'0';

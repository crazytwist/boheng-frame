-- =============================================================================
-- AGV 受理应答按业务码判定 + 回调不超时（幂等）
--
-- 背景一：原来 acceptCallback 只看 HTTP 2xx。厂商的惯例是 HTTP 200 + 业务码拒绝
--   （AGV 回 {"code":1,"message":"任务已满"}、HPLC 回 {"IsAccepted":false}），
--   这种应答按「已受理」放过去，命令就停在等回调，永远等不到，最后表现成超时。
--   现在受理应答也走动作上的 result_rule，这里给 AGV 配上它的规则。
--
-- 背景二：AGV 的 4 个步骤（AGV-Task01..04）共用这一个动作。设备域超时和流程节点超时
--   都是 300 秒、同时到期，谁先到看运气。按「超时只在一个地方判」的原则，
--   设备域改成不超时（callback_timeout_sec = 0），由流程节点的 timeout_ms 兜底。
--
--   效果与代价：
--     · 命令 timeout_at 留空，超时扫描器不会把它收口
--     · 回调丢了就一直挂着，只能人工结束：POST /admin-api/device/command/close
--     · 扫描器对这类命令每小时打一次告警计数，不让它彻底静默
--
-- 回滚：
--   UPDATE device_action SET callback_timeout_sec = NULL,
--          result_rule = NULL WHERE action_code='genAgvSchedulingTask' AND device_type_code='AGV';
--
-- 执行：
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 \
--     -D boheng-frame < sql/mysql/device-agv-ack-migrate.sql
-- =============================================================================

SET NAMES utf8mb4;

-- 1. 判定规则：code=0 受理，说明取 message，method=end 才算终态 -----------------
--    · 受理应答（一问的第一答）：{"code":0,"data":"...","message":"成功","reqCode":"..."} → code=0 即受理
--    · 第二答回调（真实报文）：没有 code / message 字段，只有 method=end 表示任务结束
--        {"reqCode":"...","method":"end","taskCode":"...","robotCode":"6001","podCode":"100001",...}
--      规则里 code 那条在这份报文上取不到值，按「字段缺失跳过」放行；
--      method 配成终态判断，防止 RCS 推来中间回调（非 end）时被当成结果提前结算。
SET @rule := '{"successCodePath":"$.code","successCodeValues":["0"],"messagePath":"$.message","settlePath":"$.method","settleValues":["end"]}';
UPDATE `device_action`
SET `result_rule` = @rule,
    `updater`     = 'yinan'
WHERE `action_code` = 'genAgvSchedulingTask'
  AND `device_type_code` = 'AGV'
  AND `deleted` = 0;

-- 2. 回调不超时：0 = 一直等，由流程节点超时兜底 ---------------------------
UPDATE `device_action`
SET `callback_timeout_sec` = 0,
    `updater`              = 'yinan'
WHERE `action_code` = 'genAgvSchedulingTask'
  AND `device_type_code` = 'AGV'
  AND `deleted` = 0;

-- 3. 核对：动作配置 ------------------------------------------------------
SELECT `action_code`, `result_mode`, `callback_timeout_sec`, `result_rule`
FROM `device_action`
WHERE `action_code` = 'genAgvSchedulingTask' AND `device_type_code` = 'AGV' AND `deleted` = 0;

-- 4. 核对：共用的 4 个步骤（超时仍由流程节点决定，这里只确认没被改） ---------
SELECT s.`step_key`, s.`name`, s.`timeout_ms`,
       JSON_UNQUOTE(JSON_EXTRACT(s.`type_config`, '$.deviceCode'))  AS device_code,
       JSON_UNQUOTE(JSON_EXTRACT(s.`type_config`, '$.actionCode'))  AS action_code
FROM `flow_step` s
WHERE s.`step_type` = 'device.command' AND s.`deleted` = 0
ORDER BY s.`step_key`;

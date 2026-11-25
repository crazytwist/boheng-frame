-- ============================================================================
-- 外部回调节点（external.callback）能力目录种子
--
-- 背景：
--   external.callback 是流程侧内建的一个能力型步骤类型（ExternalCallbackStepType），
--   语义是「本节点停下来，等外部业务系统回报结果」，completionMode = SIGNAL，
--   waitingReason = EXTERNAL_CONFIRMATION。
--
--   但画布拖节点走的是能力目录（flow_step），目录里没有这一条，节点就画不出来。
--   类型注册表里有、目录里没有，是两件不同的事：
--     · StepTypeRegistry 注册   → 引擎认这个 step_type，能执行
--     · flow_step 里有一条记录  → 画布能引用，用户能拖
--   这个脚本补的是第二件事。
--
-- 与 device.command 的区别（别混用）：
--   device.command  → 指令发给设备，设备拿 commandNo 回调 /api/callback/{commandNo}
--                     「设备做了什么」由设备域判定
--   external.callback → 等任意外部系统（LIMS、MES、人工确认页）回报
--                     回调地址 /app-api/flow/external-callback/{executionId}/{nodeId}
--                     带 attempt + token，「这一次算不算完成」由节点上的 completeWhen 判定
--
-- 幂等性：
--   flow_step 上有 uk_flow_step (tenant_id, step_key_guard)，
--   step_key_guard 是 deleted=b'0' 时等于 step_key、删除后为 NULL 的生成列，
--   所以 ON DUPLICATE KEY UPDATE 只会命中「同租户下未删除的同名能力」这一行，
--   重复执行只是把名称/配置刷新成脚本里的值。已删除的行 guard 为 NULL，不参与冲突，
--   重新执行会插入一条新的，符合直觉。
--
-- 执行：
--   docker exec -i mysql-local mysql -uroot -proot \
--     --default-character-set=utf8mb4 -D boheng-frame < flow-step-external-callback.sql
-- ============================================================================

-- id 段 9320xx 留给「流程内建能力目录」，与 demo 用的 9304xx / 9305xx / 9306xx 分开。
-- tenant_id = 1：与现有 demo 数据一致，走默认租户。
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, output_schema,
                       timeout_ms, max_attempts, remark, status, tenant_id, creator)
VALUES (932001,
        'external.callback',
        '等待外部回调',
        'external.callback',
        -- counterparty 只是给这类节点起个可读的身份，会写进 waiting_ref，方便在运行详情里
        -- 一眼看出「在等谁」，例如 LIMS / MES / 人工复核台
        '{"counterparty":"external"}',
        NULL,
        -- 外部系统回报的 payload 原样落在 node.<nodeId>.result，这里声明成契约给运维看
        '{"result":"外部系统回报的结果对象","success":"外部系统自报的成功标记"}',
        -- 默认等 30 分钟：外部业务系统的确认通常是人或另一个系统的节奏，比设备动作慢一个量级
        1800000,
        -- 不重试：等待类节点重试没有意义，超时该走失败策略而不是把同一次等待再等一遍
        1,
        '等待外部业务系统回报结果。回调地址 /app-api/flow/external-callback/{executionId}/{nodeId}，'
        '需带 attempt 与 token；是否算完成由节点 completeWhen 断言判定',
        0,
        1,
        'system')
ON DUPLICATE KEY UPDATE name            = VALUES(name),
                        step_type       = VALUES(step_type),
                        type_config     = VALUES(type_config),
                        output_schema   = VALUES(output_schema),
                        timeout_ms      = VALUES(timeout_ms),
                        max_attempts    = VALUES(max_attempts),
                        remark          = VALUES(remark),
                        status          = VALUES(status),
                        updater         = 'system';

-- 校验：应当恰好一行，status = 0
SELECT id, step_key, step_type, timeout_ms, max_attempts, status
FROM flow_step
WHERE step_key = 'external.callback'
  AND deleted = b'0';

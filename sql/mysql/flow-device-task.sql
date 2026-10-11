-- =============================================================================
-- 实验室自动执行 · 流程设备异步任务队列
-- 设备任务不「推」给设备，而是「存」进队列由机器端主动拉取执行、执行完回报。
-- 状态机：PENDING 待领取 → CLAIMED 已领取执行中 → DONE 完成 / FAILED 失败
--         取消运行时 PENDING → CANCELLED
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `flow_device_task` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `task_no`               varchar(64)  NOT NULL COMMENT '对外任务号，机器端回报时按它定位',
    `task_no_guard`         varchar(64)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `task_no`, NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放任务号',
    `callback_token`        varchar(128) NULL COMMENT '完成回报校验令牌。入队生成，认领时下发，回报时校验',
    `execution_id`          bigint       NOT NULL COMMENT '所属运行',
    `node_id`               varchar(32)  NOT NULL COMMENT '节点编码',
    `attempt`               int          NOT NULL DEFAULT 1 COMMENT '尝试序号',
    `iteration_no`          int          NOT NULL DEFAULT 0 COMMENT '循环轮次',
    `device_code`           varchar(64)  NULL COMMENT '目标设备编码。空=不限设备',
    `action_code`           varchar(64)  NOT NULL COMMENT '任务类型/动作编码',
    `params_json`           json         NULL COMMENT '任务参数 JSON',
    `status`                varchar(32)  NOT NULL COMMENT 'PENDING 待领取 / CLAIMED 已领取执行中 / DONE 完成 / FAILED 失败 / CANCELLED 已取消',
    `claimed_by`            varchar(64)  NULL COMMENT '认领的机器端标识',
    `claimed_at`            datetime     NULL COMMENT '认领时间',
    `wait_ms`               bigint       NULL COMMENT '排队耗时=认领时间-创建时间，毫秒',
    `result_json`           json         NULL COMMENT '完成结果 JSON（写进节点输出）',
    `error_message`         varchar(512) NULL COMMENT '失败说明',
    `execute_ms`            bigint       NULL COMMENT '执行耗时=终态时间-认领时间，毫秒',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_device_task_no` (`tenant_id`, `task_no_guard`),
    KEY `idx_flow_device_task_exec` (`execution_id`, `node_id`, `attempt`),
    KEY `idx_flow_device_task_claim` (`status`, `device_code`, `action_code`),
    KEY `idx_flow_device_task_token` (`callback_token`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程设备异步任务队列。机器端拉取执行后回报';

-- 事件流水：一次状态流转一行，INSERT-ONLY 只增不删，用于审计溯源
CREATE TABLE IF NOT EXISTS `flow_device_task_event` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `task_no`               varchar(64)  NOT NULL COMMENT '关联 flow_device_task.task_no',
    `event_type`            varchar(32)  NOT NULL COMMENT 'CREATED 入队 / CLAIMED 认领 / DONE 完成 / FAILED 失败 / CANCELLED 取消',
    `from_status`           varchar(32)  NULL COMMENT '变更前状态',
    `to_status`             varchar(32)  NULL COMMENT '变更后状态',
    `claimed_by`            varchar(64)  NULL COMMENT '当时操作的机器端标识',
    `payload`               varchar(512) NULL COMMENT '附加信息 JSON',
    `occurred_at`           datetime     NOT NULL COMMENT '事件发生时间',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_flow_device_task_event_no` (`task_no`),
    KEY `idx_flow_device_task_event_time` (`occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程设备任务事件流水。INSERT-ONLY 审计';

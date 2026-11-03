-- =============================================================================
-- 统一错误记录（doc/07 E-1～E-3）
-- flow_error_record：一次可独立处置的错误事实
-- flow_error_action：只增不改的处置流水（最小集）
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `flow_error_record` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `error_no`              varchar(64)  NOT NULL COMMENT '错误业务号',
    `error_no_guard`        varchar(64)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `error_no`, NULL)) VIRTUAL COMMENT '逻辑删除后释放业务号',
    `dedup_key`             varchar(191) NOT NULL COMMENT '去重键：同一租户同一事实只一条',
    `dedup_key_guard`       varchar(191) GENERATED ALWAYS AS (IF(`deleted` = b'0', `dedup_key`, NULL)) VIRTUAL COMMENT '逻辑删除后释放去重键',
    `source_type`           varchar(32)  NOT NULL COMMENT 'FLOW_ENGINE/FLOW_STEP/WMS/DEVICE/COMMUNICATION/SYSTEM',
    `source_ref_id`         varchar(64)  NULL COMMENT '来源侧引用（如 stepExecutionId）',
    `execution_id`          bigint       NULL COMMENT '流程运行编号',
    `node_id`               varchar(32)  NULL COMMENT '节点编码',
    `iteration_no`          int          NULL COMMENT '循环轮次',
    `attempt`               int          NULL COMMENT '尝试序号',
    `device_code`           varchar(64)  NULL COMMENT '设备编码（E-3 可空）',
    `command_id`            bigint       NULL COMMENT '命令编号',
    `device_event_id`       bigint       NULL COMMENT '设备错误事件编号（E-4）',
    `operation_id`          varchar(64)  NULL COMMENT '业务操作批次号',
    `error_code`            varchar(64)  NOT NULL COMMENT '平台错误码',
    `category`              varchar(32)  NOT NULL COMMENT '错误分类',
    `severity`             varchar(16)  NOT NULL COMMENT 'INFO/WARNING/ERROR/CRITICAL',
    `certainty`             varchar(16)  NOT NULL DEFAULT 'CONFIRMED' COMMENT 'CONFIRMED/UNKNOWN',
    `retry_mode`            varchar(32)  NOT NULL DEFAULT 'NEVER' COMMENT 'SAFE/IDEMPOTENT/QUERY_FIRST/MANUAL/NEVER',
    `message`               varchar(512) NOT NULL COMMENT '可读说明',
    `detail_json`           json         NULL COMMENT '结构化细节',
    `raw_payload_ref`       varchar(128) NULL COMMENT '原始报文引用',
    `status`                varchar(32)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/WAITING_RETRY/WAITING_EXTERNAL_CONFIRMATION/WAITING_MANUAL/RESOLVED/IGNORED',
    `resolution`            varchar(64)  NULL COMMENT '处置结论',
    `resolved_by`           varchar(64)  NULL COMMENT '处置人',
    `resolved_at`           datetime     NULL COMMENT '处置时间',
    `occurred_at`           datetime     NOT NULL COMMENT '错误发生时间',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_error_no` (`tenant_id`, `error_no_guard`),
    UNIQUE KEY `uk_error_dedup` (`tenant_id`, `dedup_key_guard`),
    KEY `idx_flow_error_execution` (`execution_id`, `node_id`, `attempt`),
    KEY `idx_flow_error_status` (`status`, `occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程错误记录';

CREATE TABLE IF NOT EXISTS `flow_error_action` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `error_id`              bigint       NOT NULL COMMENT 'flow_error_record.id',
    `action`                varchar(32)  NOT NULL COMMENT 'RETRY/ACKNOWLEDGE/COMMENT/...',
    `operator`              varchar(64)  NULL COMMENT '操作者',
    `action_result`         varchar(32)  NULL COMMENT '结果摘要：ACCEPTED/REJECTED/DONE',
    `comment`               varchar(512) NULL COMMENT '说明',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_flow_error_action_error` (`error_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程错误处置流水（只增不改）';

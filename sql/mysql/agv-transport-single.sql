-- =============================================================================
-- 单 AGV 搬运 · 任务队列 + 槽位预占（P0，无 resource 模块、无求解器）
-- 底座: boheng-frame    目标库: MySQL 8.0+
-- 依赖:
--   wms_slot_info / wms_material_instance / wms_material_movement（已有）
--   flow_definition / flow_execution（已有）
-- 用法:
--   1) 业务写入 agv_transport_task（PENDING），库位/实例由调用方指定；
--   2) 调度 Job 同一 agv_device_code 串行：抢任务 → 锁目标槽 → start Flow → 回写状态；
--   3) Flow 内 validate → device.command → bean transfer；成功 RELEASE 槽位预占。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 搬运任务（多批次 = 多行任务，单 AGV 同时仅一条 RUNNING）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agv_transport_task` (
    `id`                      bigint       NOT NULL COMMENT '主键(雪花)',
    `task_no`                 varchar(64)  NOT NULL COMMENT '搬运任务号，对外展示',
    `task_no_guard`           varchar(64)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `task_no`, NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放任务号',
    `status`                  varchar(32)  NOT NULL COMMENT 'PENDING 待调度 / DISPATCHED 已派发未运行 / RUNNING 运行中 / SUCCESS 成功 / FAILED 失败 / CANCELLED 已取消',
    `priority`                int          NOT NULL DEFAULT 100 COMMENT '越小越优先，同 AGV 队列排序用',
    `agv_device_code`         varchar(64)  NOT NULL COMMENT '指定 AGV 设备编码（P0 单台）',

    `instance_id`             bigint       NOT NULL COMMENT '搬运的顶层物料实例 wms_material_instance.id',
    `instance_code`           varchar(64)  NOT NULL COMMENT '实例编码冗余',
    `from_slot_id`            bigint       NOT NULL COMMENT '源槽位 wms_slot_info.id',
    `from_slot_code`          varchar(64)  NOT NULL COMMENT '源槽位编码冗余',
    `to_slot_id`              bigint       NOT NULL COMMENT '目标槽位 wms_slot_info.id',
    `to_slot_code`            varchar(64)  NOT NULL COMMENT '目标槽位编码冗余',

    `batch_id`                varchar(64)  NULL COMMENT '业务批次号（可选，追溯）',
    `sample_id`               varchar(64)  NULL COMMENT '样本业务编号（可选）',
    `remark`                  varchar(512) NULL COMMENT '说明',

    `pipeline_key`            varchar(64)  NOT NULL COMMENT '绑定的流程编码，如 agv.move.single',
    `definition_id`           bigint       NULL COMMENT '流程定义 id；空则调度时解析已发布最新版',
    `flow_execution_id`       bigint       NULL COMMENT '关联 flow_execution.id，派发后写入',
    `flow_execution_no`       varchar(64)  NULL COMMENT '关联 flow_execution.execution_no 冗余',

    `wms_movement_id`         bigint       NULL COMMENT '成功后 wms_material_movement.id（MOVE）',
    `idempotent_key`          varchar(128) NULL COMMENT '幂等键；同一租户重复提交只保留一条有效任务',
    `idempotent_guard`        varchar(128) GENERATED ALWAYS AS (IF(`deleted` = b'0', `idempotent_key`, NULL)) VIRTUAL COMMENT '生成列。幂等唯一',

    `dispatched_at`           datetime     NULL COMMENT '派发 Flow 时间',
    `started_at`              datetime     NULL COMMENT 'Flow 进入 RUNNING 时间（可选，由同步 Job 写）',
    `finished_at`             datetime     NULL COMMENT '终态时间',
    `error_code`              varchar(64)  NULL COMMENT '失败码',
    `error_message`           varchar(512) NULL COMMENT '失败说明',

    `row_version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `creator`                 varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                 varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                 bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_agv_task_no` (`tenant_id`, `task_no_guard`),
    UNIQUE KEY `uk_agv_task_idempotent` (`tenant_id`, `idempotent_guard`),
    KEY `idx_agv_task_queue` (`tenant_id`, `agv_device_code`, `status`, `priority`, `create_time`),
    KEY `idx_agv_task_execution` (`flow_execution_id`),
    KEY `idx_agv_task_instance` (`instance_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AGV 搬运任务队列（单 AGV 串行消费）';


-- -----------------------------------------------------------------------------
-- 2. 槽位预占（无 resource 模块时的轻量锁；与 wms_slot_info.slot_status=LOCKED 配合）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agv_slot_hold` (
    `id`                      bigint       NOT NULL COMMENT '主键(雪花)',
    `slot_id`                 bigint       NOT NULL COMMENT '槽位 wms_slot_info.id',
    `slot_code`               varchar(64)  NOT NULL COMMENT '槽位编码冗余',
    `hold_role`               varchar(16)  NOT NULL COMMENT 'TARGET 目标位预占 / SOURCE 源位保护（可选）',
    `task_id`                 bigint       NOT NULL COMMENT 'agv_transport_task.id',
    `status`                  varchar(16)  NOT NULL COMMENT 'ACTIVE 生效中 / RELEASED 已释放',
    `active_guard`            bigint       GENERATED ALWAYS AS (IF(`deleted` = b'0' AND `status` = 'ACTIVE', `slot_id`, NULL)) VIRTUAL COMMENT '生成列。同槽位仅一条 ACTIVE',
    `expire_time`             datetime     NULL COMMENT '预占过期时间，Job 可回收',
    `release_reason`          varchar(64)  NULL COMMENT '释放原因：SUCCESS / FAILED / CANCEL / EXPIRED',
    `creator`                 varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                 varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                 bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`               bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_agv_slot_hold_active` (`tenant_id`, `active_guard`),
    KEY `idx_agv_slot_hold_task` (`task_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AGV 搬运槽位预占（P0 替代 resource 租约）';

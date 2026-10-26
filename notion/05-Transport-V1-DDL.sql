-- =============================================================================
-- Transport 运输域 V1.0 DDL
-- 底座: boheng-frame    目标库: MySQL 8.0+
--
-- 设计边界:
--   1) Transport 是运输业务域，AGV 只是 V1.0 的首个执行载体。
--   2) AGV/机械臂/门禁/机台档案与命令由 device 域负责；物料与位置由 WMS 负责。
--   3) V1.0 不依赖 scheduler/resource 独立服务，使用 transport_local_lock 本地锁。
--   4) 后续通过 allocation_mode 与 lock_mode 切换 Scheduler/Resource，不重构运输主表。
--   5) 跨模块只保存 ID、编码和必要快照，不建立数据库外键。
--
-- 表清单:
--   transport_request             运输业务需求
--   transport_order               可执行运输单
--   transport_item                运输对象明细
--   transport_stop                有序停靠点
--   transport_operation           原子执行动作
--   transport_handover_session    双边交接会话
--   transport_handover_evidence   交接传感器/扫码证据
--   transport_lock_ref            运输单与锁/租约关联
--   transport_local_lock          V1 本地资源锁
--   transport_event               运输事件流水
--
-- 用法:
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 boheng-frame \
--     < sql/mysql/transport-v1.sql
-- =============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. transport_request 运输需求
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_request` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `request_no`             varchar(64)   NOT NULL COMMENT '运输需求编号(租户内唯一)',
    `request_no_guard`       varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `request_no`, NULL)) VIRTUAL COMMENT '生成列，逻辑删除后释放需求编号',
    `request_type`           varchar(32)   NOT NULL DEFAULT 'MATERIAL_TRANSFER' COMMENT '需求类型: MATERIAL_TRANSFER 物料转运/LOAD 上料/UNLOAD 下料/RETURN 返程',
    `source_type`            varchar(32)   NOT NULL COMMENT '来源类型: FLOW/WMS/MANUAL/EXTERNAL',
    `source_ref`             varchar(128)  NOT NULL COMMENT '来源业务引用(流程执行号、WMS单号或外部请求号)',
    `project_id`             bigint        NULL COMMENT '项目编号(可空)',
    `experiment_id`          bigint        NULL COMMENT '实验编号(可空)',
    `execution_id`           bigint        NULL COMMENT '流程执行编号(可空)',
    `priority`               int           NOT NULL DEFAULT 100 COMMENT '优先级，数值越大越优先',
    `earliest_start_time`    datetime      NULL COMMENT '最早允许开始时间',
    `latest_finish_time`     datetime      NULL COMMENT '最迟完成时间',
    `transport_mode`         varchar(32)   NOT NULL DEFAULT 'SINGLE' COMMENT '运输模式: SINGLE 单次/SAME_DESTINATION 同终点/MILK_RUN 多站巡回',
    `constraint_json`        json          NULL COMMENT '运输约束快照: 载重、温区、洁净、混载、执行器和路线等',
    `status`                 varchar(32)   NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/PLANNING/ALLOCATED/EXECUTING/COMPLETED/FAILED/CANCELLED',
    `active_order_id`        bigint        NULL COMMENT '当前生效运输单编号',
    `idempotency_key`        varchar(128)  NULL COMMENT '调用方幂等键',
    `idempotency_guard`      varchar(128)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `idempotency_key`, NULL)) VIRTUAL COMMENT '生成列，租户内有效幂等键唯一',
    `remark`                 varchar(512)  NULL COMMENT '备注',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_request_no` (`tenant_id`, `request_no_guard`),
    UNIQUE KEY `uk_transport_request_idempotent` (`tenant_id`, `idempotency_guard`),
    KEY `idx_transport_request_queue` (`tenant_id`, `status`, `priority`, `earliest_start_time`),
    KEY `idx_transport_request_source` (`tenant_id`, `source_type`, `source_ref`),
    KEY `idx_transport_request_execution` (`execution_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输需求';

-- ----------------------------------------------------------------------------
-- 2. transport_order 可执行运输单
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_order` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `order_no`              varchar(64)   NOT NULL COMMENT '运输单编号(租户内唯一)',
    `order_no_guard`        varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `order_no`, NULL)) VIRTUAL COMMENT '生成列，逻辑删除后释放运输单号',
    `request_id`            bigint        NOT NULL COMMENT '来源运输需求编号',
    `plan_version`          int           NOT NULL DEFAULT 1 COMMENT '运输方案版本，重排时递增',
    `primary_executor_type` varchar(32)   NULL COMMENT '主要执行器类型: AGV/AMR/CONVEYOR/MANUAL/COMPOSITE',
    `primary_executor_id`   bigint        NULL COMMENT '主要执行器引用(Device域设备ID)',
    `primary_executor_code` varchar(64)   NULL COMMENT '主要执行器编码冗余',
    `allocation_mode`       varchar(16)   NOT NULL DEFAULT 'LOCAL' COMMENT '分配来源: LOCAL 本地规则/SCHEDULER 调度模块',
    `allocation_ref`        varchar(128)  NULL COMMENT '本地分配记录或Scheduler方案引用',
    `current_stop_seq`      int           NULL COMMENT '当前停靠点序号',
    `route_plan_json`       json          NULL COMMENT '路线与停靠方案快照',
    `score_detail_json`     json          NULL COMMENT '方案评分和淘汰原因快照',
    `status`                varchar(32)   NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/ALLOCATING/READY/DISPATCHED/EXECUTING/COMPLETED/PARTIALLY_COMPLETED/FAILED/CANCELLED/MANUAL_INTERVENTION',
    `dispatch_at`           datetime      NULL COMMENT '下发时间',
    `start_at`              datetime      NULL COMMENT '实际开始时间',
    `finish_at`             datetime      NULL COMMENT '实际完成时间',
    `failure_code`          varchar(64)   NULL COMMENT '失败编码',
    `failure_message`       varchar(512)  NULL COMMENT '失败说明',
    `version`               int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_order_no` (`tenant_id`, `order_no_guard`),
    UNIQUE KEY `uk_transport_order_plan` (`tenant_id`, `request_id`, `plan_version`),
    KEY `idx_transport_order_executor` (`tenant_id`, `primary_executor_id`, `status`),
    KEY `idx_transport_order_status` (`tenant_id`, `status`, `dispatch_at`),
    KEY `idx_transport_order_request` (`request_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport可执行运输单';

-- ----------------------------------------------------------------------------
-- 3. transport_item 运输对象明细
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_item` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `item_no`               varchar(64)   NOT NULL COMMENT '运输明细编号',
    `order_id`              bigint        NOT NULL COMMENT '所属运输单编号',
    `request_id`            bigint        NULL COMMENT '来源运输需求编号',
    `material_instance_id`  bigint        NULL COMMENT 'WMS物料实例编号',
    `load_unit_id`          bigint        NULL COMMENT 'WMS载具编号',
    `load_unit_code`        varchar(128)  NULL COMMENT '载具条码/编码快照',
    `batch_no`              varchar(64)   NULL COMMENT '业务批次号',
    `quantity`              decimal(18,6) NOT NULL DEFAULT 1 COMMENT '运输数量',
    `source_slot_id`        bigint        NOT NULL COMMENT '原始起点槽位编号',
    `source_slot_code`      varchar(64)   NOT NULL COMMENT '原始起点槽位编码快照',
    `target_slot_id`        bigint        NOT NULL COMMENT '最终目标槽位编号',
    `target_slot_code`      varchar(64)   NOT NULL COMMENT '最终目标槽位编码快照',
    `current_slot_id`       bigint        NULL COMMENT '当前位置槽位编号快照，WMS为真相源',
    `current_slot_code`     varchar(64)   NULL COMMENT '当前位置槽位编码快照',
    `transport_load_slot_id` bigint       NULL COMMENT '运输载体上的载位Slot编号',
    `pickup_stop_id`        bigint        NULL COMMENT '取料停靠点编号',
    `dropoff_stop_id`       bigint        NULL COMMENT '放料停靠点编号',
    `mix_group`             varchar(64)   NULL COMMENT '混载兼容组',
    `sequence_no`           int           NOT NULL DEFAULT 0 COMMENT '运输对象执行顺序',
    `status`                varchar(32)   NOT NULL DEFAULT 'WAITING_PICKUP' COMMENT '状态: WAITING_PICKUP/PICKING/ON_TRANSPORT/DROPPING/DELIVERED/PICK_FAILED/DROPOFF_FAILED/LOCATION_UNKNOWN/RETURNING/RETURNED',
    `last_movement_id`      bigint        NULL COMMENT '最近一次WMS移动流水编号',
    `loaded_at`             datetime      NULL COMMENT '实际装载时间',
    `unloaded_at`           datetime      NULL COMMENT '实际卸载时间',
    `version`               int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_item_no` (`tenant_id`, `order_id`, `item_no`),
    KEY `idx_transport_item_order` (`order_id`, `status`, `sequence_no`),
    KEY `idx_transport_item_material` (`material_instance_id`),
    KEY `idx_transport_item_slot` (`current_slot_id`, `status`),
    KEY `idx_transport_item_batch` (`tenant_id`, `batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输对象明细';

-- ----------------------------------------------------------------------------
-- 4. transport_stop 有序停靠点
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_stop` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `order_id`              bigint        NOT NULL COMMENT '所属运输单编号',
    `stop_seq`              int           NOT NULL COMMENT '停靠顺序，建议使用10/20/30便于插入重排点',
    `stop_type`             varchar(16)   NOT NULL COMMENT '停靠职责: PICKUP/DROPOFF/MIXED/WAIT/CHARGE/PARK',
    `location_type`         varchar(32)   NOT NULL COMMENT '位置类型: STORAGE/MACHINE/BUFFER/DOOR/ELEVATOR/CHARGE',
    `location_id`           bigint        NULL COMMENT '业务位置编号',
    `location_code`         varchar(64)   NULL COMMENT '业务位置编码',
    `transfer_slot_id`      bigint        NULL COMMENT 'WMS中转槽位编号',
    `interface_device_id`   bigint        NULL COMMENT '机台/门禁等交互设备编号',
    `route_segment_ref`     varchar(128)  NULL COMMENT '路线分段引用，未来Scheduler使用',
    `planned_arrival_at`    datetime      NULL COMMENT '预计到达时间',
    `actual_arrival_at`    datetime      NULL COMMENT '实际到达时间',
    `planned_departure_at`  datetime      NULL COMMENT '预计离开时间',
    `actual_departure_at`  datetime      NULL COMMENT '实际离开时间',
    `wait_timeout_sec`      int           NULL COMMENT '本站最大等待秒数',
    `condition_json`        json          NULL COMMENT '到达、开门、取放料条件快照',
    `status`                varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/TRAVELLING/ARRIVED/OPERATING/COMPLETED/SKIPPED/FAILED',
    `version`               int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_stop_seq` (`tenant_id`, `order_id`, `stop_seq`),
    KEY `idx_transport_stop_status` (`order_id`, `status`),
    KEY `idx_transport_stop_location` (`location_type`, `location_id`, `planned_arrival_at`),
    KEY `idx_transport_stop_slot` (`transfer_slot_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport有序停靠点';

-- ----------------------------------------------------------------------------
-- 5. transport_operation 原子执行动作
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_operation` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `operation_no`          varchar(64)   NOT NULL COMMENT '动作编号',
    `order_id`              bigint        NOT NULL COMMENT '所属运输单编号',
    `stop_id`               bigint        NOT NULL COMMENT '所属停靠点编号',
    `item_id`               bigint        NULL COMMENT '关联运输对象编号',
    `operation_seq`         int           NOT NULL COMMENT '本站动作顺序',
    `operation_type`        varchar(32)   NOT NULL COMMENT '动作类型: MOVE/DOCK/WAIT_RESOURCE/OPEN_DOOR/ROBOT_PICK/ROBOT_PLACE/VERIFY_LOAD/VERIFY_UNLOAD/CLOSE_DOOR/UNDOCK/UPDATE_LOCATION/RELEASE_RESOURCE',
    `executor_type`         varchar(32)   NULL COMMENT '执行器类型: AGV/ROBOT_ARM/MACHINE/DOOR/WMS',
    `executor_id`           bigint        NULL COMMENT '执行器设备编号',
    `device_command_id`     bigint        NULL COMMENT 'Device命令编号',
    `source_slot_id`        bigint        NULL COMMENT '动作源槽位',
    `target_slot_id`        bigint        NULL COMMENT '动作目标槽位',
    `depends_on`            varchar(512)  NULL COMMENT '依赖动作编号列表',
    `request_payload`       json          NULL COMMENT '动作请求参数快照',
    `response_payload`      json          NULL COMMENT '动作结果快照',
    `idempotency_key`       varchar(128)  NOT NULL COMMENT '动作幂等键',
    `status`                varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/WAITING/SUCCEEDED/FAILED/SKIPPED/CANCELLED',
    `attempt`               int           NOT NULL DEFAULT 0 COMMENT '当前尝试次数',
    `max_attempts`          int           NOT NULL DEFAULT 3 COMMENT '最大尝试次数',
    `timeout_at`            datetime      NULL COMMENT '动作超时时间',
    `started_at`            datetime      NULL COMMENT '开始时间',
    `finished_at`           datetime      NULL COMMENT '完成时间',
    `error_code`            varchar(64)   NULL COMMENT '错误编码',
    `error_message`         varchar(512)  NULL COMMENT '错误说明',
    `version`               int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_operation_no` (`tenant_id`, `operation_no`),
    UNIQUE KEY `uk_transport_operation_idempotent` (`tenant_id`, `idempotency_key`),
    KEY `idx_transport_operation_order` (`order_id`, `stop_id`, `operation_seq`),
    KEY `idx_transport_operation_status` (`status`, `timeout_at`),
    KEY `idx_transport_operation_command` (`device_command_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport原子执行动作';

-- ----------------------------------------------------------------------------
-- 6. transport_handover_session 双边交接会话
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_handover_session` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `handover_no`           varchar(64)   NOT NULL COMMENT '交接会话编号',
    `order_id`              bigint        NOT NULL COMMENT '所属运输单编号',
    `stop_id`               bigint        NOT NULL COMMENT '所属停靠点编号',
    `operation_id`          bigint        NOT NULL COMMENT '对应原子动作编号',
    `item_id`               bigint        NOT NULL COMMENT '交接运输对象编号',
    `handover_type`         varchar(32)   NOT NULL COMMENT '交接类型: STORAGE_TO_TRANSPORT/TRANSPORT_TO_MACHINE/MACHINE_TO_TRANSPORT/TRANSPORT_TO_STORAGE',
    `from_slot_id`          bigint        NOT NULL COMMENT '交出方槽位',
    `to_slot_id`            bigint        NOT NULL COMMENT '接收方槽位',
    `provider_device_id`    bigint        NULL COMMENT '交出方设备编号',
    `receiver_device_id`    bigint        NULL COMMENT '接收方设备编号',
    `provider_status`       varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '交出方确认状态',
    `receiver_status`       varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '接收方确认状态',
    `status`                varchar(24)   NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/PROVIDER_READY/RECEIVER_READY/TRANSFERRING/VERIFYING/CONFIRMED/INCONSISTENT/TIMEOUT/FAILED/MANUAL_CONFIRMED/ROLLED_BACK',
    `wms_movement_id`       bigint        NULL COMMENT '双边确认后生成的WMS移动流水编号',
    `started_at`            datetime      NULL COMMENT '交接开始时间',
    `confirmed_at`          datetime      NULL COMMENT '双边确认时间',
    `timeout_at`            datetime      NULL COMMENT '交接超时时间',
    `failure_code`          varchar(64)   NULL COMMENT '失败编码',
    `failure_message`       varchar(512)  NULL COMMENT '失败说明',
    `version`               int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_handover_no` (`tenant_id`, `handover_no`),
    KEY `idx_transport_handover_order` (`order_id`, `stop_id`),
    KEY `idx_transport_handover_status` (`status`, `timeout_at`),
    KEY `idx_transport_handover_item` (`item_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport双边交接会话';

-- ----------------------------------------------------------------------------
-- 7. transport_handover_evidence 交接证据(只增不改)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_handover_evidence` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `session_id`            bigint        NOT NULL COMMENT '交接会话编号',
    `evidence_side`         varchar(16)   NOT NULL COMMENT '证据侧: PROVIDER/RECEIVER/OBSERVER',
    `evidence_type`         varchar(32)   NOT NULL COMMENT '证据类型: LOAD_SENSOR/SLOT_SENSOR/BARCODE/RFID/VISION/DOOR_STATE/LIGHT_CURTAIN/ARM_POSITION/PLC_INTERLOCK',
    `device_id`             bigint        NULL COMMENT '证据来源设备编号',
    `device_command_id`     bigint        NULL COMMENT '来源Device命令编号',
    `expected_value`        varchar(512)  NULL COMMENT '期望值',
    `actual_value`          varchar(512)  NULL COMMENT '实际值',
    `matched`               bit(1)        NULL COMMENT '是否匹配',
    `evidence_payload`      json          NULL COMMENT '完整原始证据报文',
    `collected_at`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '证据采集时间',
    `valid`                 bit(1)        NOT NULL DEFAULT b'1' COMMENT '证据是否有效',
    `invalid_reason`        varchar(255)  NULL COMMENT '证据无效原因',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_transport_evidence_session` (`session_id`, `collected_at`),
    KEY `idx_transport_evidence_device` (`device_id`, `device_command_id`),
    KEY `idx_transport_evidence_type` (`evidence_type`, `valid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport交接证据(只增不改)';

-- ----------------------------------------------------------------------------
-- 8. transport_lock_ref 运输单与锁/租约关联
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_lock_ref` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `order_id`              bigint        NOT NULL COMMENT '运输单编号',
    `stop_id`               bigint        NULL COMMENT '停靠点编号',
    `operation_id`          bigint        NULL COMMENT '原子动作编号',
    `lock_mode`             varchar(16)   NOT NULL DEFAULT 'LOCAL' COMMENT '锁来源: LOCAL 本地锁/RESOURCE Resource租约',
    `lock_ref`              varchar(128)  NOT NULL COMMENT '本地锁ID或Resource租约ID',
    `resource_type`         varchar(32)   NOT NULL COMMENT '资源类型: EXECUTOR/LOAD_SLOT/TRANSFER_SLOT/MACHINE_INTERFACE/DOOR/LOAD_UNIT/ROUTE_SEGMENT/ELEVATOR/CHARGER',
    `resource_key`          varchar(128)  NOT NULL COMMENT '资源唯一键',
    `scope_type`            varchar(16)   NOT NULL COMMENT '持有范围: ORDER/STOP/OPERATION',
    `fencing_token`         bigint        NULL COMMENT '防止旧持有者操作的递增令牌',
    `status`                varchar(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/RELEASED/EXPIRED',
    `acquired_at`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    `expire_at`             datetime      NULL COMMENT '租约过期时间',
    `released_at`           datetime      NULL COMMENT '释放时间',
    `release_reason`        varchar(64)   NULL COMMENT '释放原因',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_transport_lock_ref_order` (`order_id`, `status`),
    KEY `idx_transport_lock_ref_resource` (`tenant_id`, `resource_type`, `resource_key`, `status`),
    KEY `idx_transport_lock_ref_expire` (`status`, `expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输锁/租约关联';

-- ----------------------------------------------------------------------------
-- 9. transport_local_lock V1 本地资源锁
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_local_lock` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `resource_type`         varchar(32)   NOT NULL COMMENT '资源类型',
    `resource_key`          varchar(128)  NOT NULL COMMENT '资源唯一键',
    `order_id`              bigint        NOT NULL COMMENT '持锁运输单编号',
    `lock_token`            varchar(128)  NOT NULL COMMENT '随机锁令牌',
    `fencing_token`         bigint        NOT NULL DEFAULT 0 COMMENT '单调递增防旧令牌',
    `status`                varchar(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/RELEASED/EXPIRED',
    `active_guard`           varchar(160)  GENERATED ALWAYS AS (IF(`deleted` = b'0' AND `status` = 'ACTIVE', CONCAT(`resource_type`, '#', `resource_key`), NULL)) VIRTUAL COMMENT '生成列，同一租户同一资源仅允许一条有效锁',
    `acquired_at`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    `expire_at`             datetime      NOT NULL COMMENT '租约过期时间',
    `heartbeat_at`          datetime      NULL COMMENT '最近续租时间',
    `released_at`           datetime      NULL COMMENT '释放时间',
    `version`               int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_local_lock_active` (`tenant_id`, `active_guard`),
    UNIQUE KEY `uk_transport_local_lock_token` (`tenant_id`, `lock_token`),
    KEY `idx_transport_local_lock_order` (`order_id`, `status`),
    KEY `idx_transport_local_lock_expire` (`status`, `expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport V1本地资源锁';

-- ----------------------------------------------------------------------------
-- 10. transport_event 运输事件流水(只增不改)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transport_event` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `event_no`              varchar(64)   NOT NULL COMMENT '事件编号',
    `request_id`            bigint        NULL COMMENT '运输需求编号',
    `order_id`              bigint        NULL COMMENT '运输单编号',
    `stop_id`               bigint        NULL COMMENT '停靠点编号',
    `operation_id`          bigint        NULL COMMENT '动作编号',
    `item_id`               bigint        NULL COMMENT '运输对象编号',
    `handover_id`           bigint        NULL COMMENT '交接会话编号',
    `event_type`            varchar(64)   NOT NULL COMMENT '事件类型',
    `before_status`         varchar(32)   NULL COMMENT '变更前状态',
    `after_status`          varchar(32)   NULL COMMENT '变更后状态',
    `event_payload`         json          NULL COMMENT '事件内容快照',
    `source_type`           varchar(32)   NULL COMMENT '来源类型: TRANSPORT/DEVICE/WMS/FLOW/MANUAL',
    `source_ref`            varchar(128)  NULL COMMENT '来源引用',
    `occurred_at`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务发生时间',
    `published`             bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否已发布Outbox事件',
    `published_at`          datetime      NULL COMMENT '发布时间',
    `trace_id`              varchar(64)   NULL COMMENT '全链路追踪ID',
    `idempotency_key`       varchar(128)  NULL COMMENT '事件幂等键',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_event_no` (`tenant_id`, `event_no`),
    UNIQUE KEY `uk_transport_event_idempotent` (`tenant_id`, `idempotency_key`),
    KEY `idx_transport_event_order` (`order_id`, `occurred_at`),
    KEY `idx_transport_event_publish` (`published`, `occurred_at`),
    KEY `idx_transport_event_type` (`event_type`, `occurred_at`),
    KEY `idx_transport_event_source` (`source_type`, `source_ref`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输事件流水(只增不改)';


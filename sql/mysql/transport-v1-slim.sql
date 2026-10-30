-- =============================================================================
-- Transport 运输域 V1.0 收敛版 DDL（点对点 + 多件装载 + 托板模式）
-- 底座: boheng-frame    目标库: MySQL 8.0+
--
-- 与 transport-v1.sql 的差异（旧文件保留为历史参考，不再执行）:
--   1) 删除 transport_request.execution_id（flow 执行号由 source_type + source_ref 承载）
--   2) transport_order 增加多件装载字段: target_item_count / loaded_item_count /
--      current_cycle_no / plate_type
--   3) transport_item 增加 load_cycle_no（这件物料是第几轮装上的）
--   4) transport_stop 增加 cycle_no；stop_seq 约定 PICKUP=10/20/30、DROPOFF=900+
--   5) transport_handover_session 删除 wms_movement_id，改为 movement_batch_no
--      （多件物料对应 N 条移动流水，逐条记在 transport_item.last_movement_id）
--   6) transport_handover_evidence / transport_lock_ref / transport_event
--      补齐 creator/create_time/updater/update_time/deleted 五个审计列
--      （DO 继承 TenantBaseDO，缺列会导致 MyBatis-Plus 拼出非法 SQL）
--   7) transport_local_lock / transport_lock_ref 的 resource_type 收缩：
--      槽位类资源（TRANSFER_SLOT/LOAD_SLOT/LOAD_UNIT）归 WMS 的
--      TransportWmsApi 管理，本地锁只负责无归属模块的资源
--   8) transport_route_config 与 flow 解绑，删除 flow_definition_key/version；
--      匹配键降级为 类型 + 区域，站点编码变为可选精确定位
--   9) 所有唯一键统一使用 guard 生成列，逻辑删除后可复用编码
--
-- 执行说明:
--   当前 10 张 transport_* 表均为空表（已核对 count = 0），本脚本先 DROP 再建。
--   若库中已有数据，请勿直接执行，改用 ALTER 迁移。
--
-- 用法:
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 boheng-frame \
--     < sql/mysql/transport-v1-slim.sql
-- =============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `transport_route_config`;
DROP TABLE IF EXISTS `transport_request`;
DROP TABLE IF EXISTS `transport_order`;
DROP TABLE IF EXISTS `transport_item`;
DROP TABLE IF EXISTS `transport_stop`;
DROP TABLE IF EXISTS `transport_operation`;
DROP TABLE IF EXISTS `transport_local_lock`;
DROP TABLE IF EXISTS `transport_lock_ref`;
DROP TABLE IF EXISTS `transport_handover_session`;
DROP TABLE IF EXISTS `transport_handover_evidence`;
DROP TABLE IF EXISTS `transport_event`;

-- ----------------------------------------------------------------------------
-- 1. transport_route_config 静态工艺模板（只读配置，不承载运行数据）
--    作用: 按「起点类型/区域 -> 终点类型/区域」匹配出动作能力键与载荷能力
--    注意: 不绑定流程；流程在 Flow 定义中以 routeCode 作为启动参数引用本表
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_route_config` (
    `id`                        bigint       NOT NULL COMMENT '主键(雪花)',
    `route_code`                varchar(64)  NOT NULL COMMENT '工艺编码(租户内唯一)',
    `route_code_guard`          varchar(64)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `route_code`, NULL)) VIRTUAL COMMENT '生成列，逻辑删除后释放编码',
    `route_name`                varchar(128) NOT NULL COMMENT '工艺名称',
    `route_type`                varchar(32)  NOT NULL DEFAULT 'AGV' COMMENT '执行载体类型: AGV/CONVEYOR/ROBOT_ARM/MANUAL',
    `source_type`               varchar(32)  NOT NULL COMMENT '起点类型: STORAGE/MACHINE/BUFFER/DOOR',
    `source_zone_code`          varchar(64)  NULL COMMENT '起点区域编码(V1 按区域匹配)',
    `source_station_code`       varchar(64)  NULL COMMENT '起点站点编码(可选精确定位，为空表示整区域)',
    `target_type`               varchar(32)  NOT NULL COMMENT '终点类型: STORAGE/MACHINE/BUFFER/DOOR',
    `target_zone_code`          varchar(64)  NULL COMMENT '终点区域编码',
    `target_station_code`       varchar(64)  NULL COMMENT '终点站点编码(可选精确定位)',
    `move_action_key`           varchar(128) NOT NULL COMMENT '移动动作能力键(如 AGV#move)',
    `grab_action_key`           varchar(128) NOT NULL COMMENT '取料动作能力键(如 AGV#grab)',
    `place_action_key`          varchar(128) NOT NULL COMMENT '放料动作能力键(如 AGV#place)',
    `warehouse_load_action_key` varchar(128) NULL COMMENT '仓储出料动作能力键',
    `warehouse_unload_action_key` varchar(128) NULL COMMENT '仓储入料动作能力键',
    `agv_task_type`             varchar(64)  NULL COMMENT 'AGV 任务类型',
    `plate_type`                varchar(64)  NULL COMMENT '托板类型(AGV 工装，非 WMS 载具)',
    `agv_load_capacity`         int          NOT NULL DEFAULT 1 COMMENT '单趟最大装载件数',
    `door_required`             tinyint      NOT NULL DEFAULT 0 COMMENT '是否需要门禁',
    `door_open_action_key`      varchar(128) NULL COMMENT '开门动作能力键',
    `door_close_action_key`     varchar(128) NULL COMMENT '关门动作能力键',
    `priority`                  int          NOT NULL DEFAULT 0 COMMENT '多工艺命中时的优选级，数值越大越优先',
    `enabled`                   tinyint      NOT NULL DEFAULT 1 COMMENT '是否启用: 1 启用 / 0 停用',
    `remark`                     varchar(512) NULL COMMENT '备注',
    `creator`                   varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`               datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                   varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`               datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                   bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                 bigint       NOT NULL DEFAULT 0 COMMENT '租户编号(单现场部署，tenant 即现场)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_route_code` (`tenant_id`, `route_code_guard`),
    KEY `idx_transport_route_match` (`tenant_id`, `source_type`, `source_zone_code`, `target_type`, `target_zone_code`, `enabled`),
    KEY `idx_transport_route_priority` (`tenant_id`, `enabled`, `priority`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport静态工艺模板';

-- ----------------------------------------------------------------------------
-- 2. transport_request 运输需求
--    一个需求可拆成多趟执行单（多起点多终点时 request 1:N order）
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_request` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `request_no`             varchar(64)   NOT NULL COMMENT '需求编号(租户内唯一)',
    `request_no_guard`       varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `request_no`, NULL)) VIRTUAL COMMENT '生成列，逻辑删除后释放编号',
    `request_type`           varchar(32)   NOT NULL DEFAULT 'MATERIAL_TRANSFER' COMMENT '需求类型: MATERIAL_TRANSFER/LOAD/UNLOAD/RETURN',
    `transport_mode`         varchar(32)   NOT NULL DEFAULT 'SINGLE' COMMENT '运输模式: SINGLE 单件单趟/SAME_DESTINATION 多起点同终点/MILK_RUN 多终点巡回',
    `source_type`            varchar(32)   NOT NULL COMMENT '来源类型: FLOW/WMS/MANUAL/EXTERNAL',
    `source_ref`             varchar(128)  NOT NULL COMMENT '来源业务引用(流程执行号/WMS单号/外部请求号)',
    `project_id`             bigint        NULL COMMENT '项目编号(可空)',
    `experiment_id`          bigint        NULL COMMENT '实验编号(可空)',
    `priority`               int           NOT NULL DEFAULT 100 COMMENT '优先级，数值越大越优先',
    `earliest_start_time`    datetime      NULL COMMENT '最早允许开始时间',
    `latest_finish_time`     datetime      NULL COMMENT '最迟完成时间',
    `constraint_json`        json          NULL COMMENT '运输约束快照: 载重/温区/洁净/混载/托板类型',
    `status`                 varchar(32)   NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/ACCEPTED/EXECUTING/COMPLETED/FAILED/CANCELLED',
    `active_order_id`        bigint        NULL COMMENT '当前生效运输单编号',
    `idempotency_key`        varchar(128)  NULL COMMENT '调用方幂等键',
    `idempotency_guard`      varchar(128)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `idempotency_key`, NULL)) VIRTUAL COMMENT '生成列，租户内有效幂等键唯一',
    `remark`                 varchar(512)  NULL COMMENT '备注',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号(单现场部署，tenant 即现场)',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_request_no` (`tenant_id`, `request_no_guard`),
    UNIQUE KEY `uk_transport_request_idempotent` (`tenant_id`, `idempotency_guard`),
    KEY `idx_transport_request_queue` (`tenant_id`, `status`, `priority`, `earliest_start_time`),
    KEY `idx_transport_request_source` (`tenant_id`, `source_type`, `source_ref`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输需求';

-- ----------------------------------------------------------------------------
-- 3. transport_order 一次执行（= 一趟运输）
--    托板模式: 一趟装 N 件物料到一个托板，满载后整体运到终点并整体放下
--    多终点预留: current_stop_seq 指向当前正在执行的 stop，DROPOFF 允许多条
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_order` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `order_no`               varchar(64)   NOT NULL COMMENT '运输单编号(租户内唯一)',
    `order_no_guard`         varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `order_no`, NULL)) VIRTUAL COMMENT '生成列，逻辑删除后释放编号',
    `request_id`             bigint        NOT NULL COMMENT '来源运输需求编号',
    `sequence_no`            int           NOT NULL DEFAULT 1 COMMENT '同一需求内的执行顺序(多趟时 1..N)',
    `plan_version`           int           NOT NULL DEFAULT 1 COMMENT '方案版本，重排时递增',
    `route_code`             varchar(64)   NULL COMMENT '命中的工艺编码快照',
    `plate_type`             varchar(64)   NULL COMMENT '托板类型快照(AGV 工装)',
    `target_item_count`      int           NOT NULL DEFAULT 1 COMMENT '计划装载件数(装载完成的判据)',
    `loaded_item_count`      int           NOT NULL DEFAULT 0 COMMENT '已装载件数(满载判据)',
    `current_cycle_no`       int           NOT NULL DEFAULT 0 COMMENT '当前装载轮次',
    `current_stop_seq`       int           NULL COMMENT '当前停靠点序号(多终点预留)',
    `primary_executor_type`  varchar(32)   NULL COMMENT '执行载体类型: AGV/AMR/CONVEYOR/MANUAL/COMPOSITE',
    `primary_executor_id`    bigint        NULL COMMENT '执行载体引用(Device域设备ID)',
    `primary_executor_code`  varchar(64)   NULL COMMENT '执行载体编码冗余',
    `allocation_mode`        varchar(16)   NOT NULL DEFAULT 'LOCAL' COMMENT '分配来源: LOCAL 本地规则/SCHEDULER 调度模块(reserved)',
    `allocation_ref`         varchar(128)  NULL COMMENT '分配记录或方案引用(reserved，V1 不写)',
    `route_plan_json`        json          NULL COMMENT '停靠序列与装载计划快照',
    `score_detail_json`      json          NULL COMMENT '方案评分快照(reserved，V1 不写)',
    `status`                 varchar(32)   NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/DISPATCHED/EXECUTING/PARTIALLY_COMPLETED/COMPLETED/FAILED/CANCELLED',
    `dispatch_at`            datetime      NULL COMMENT '下发时间',
    `start_at`               datetime      NULL COMMENT '开始时间',
    `finish_at`              datetime      NULL COMMENT '完成时间',
    `failure_code`           varchar(64)   NULL COMMENT '失败编码',
    `failure_message`        varchar(512)  NULL COMMENT '失败说明',
    `version`                int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本(同时是执行器互斥的CAS依据)',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_order_no` (`tenant_id`, `order_no_guard`),
    UNIQUE KEY `uk_transport_order_plan` (`tenant_id`, `request_id`, `plan_version`),
    KEY `idx_transport_order_executor` (`tenant_id`, `primary_executor_id`, `status`),
    KEY `idx_transport_order_status` (`tenant_id`, `status`, `dispatch_at`),
    KEY `idx_transport_order_request` (`request_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport可执行运输单';

-- ----------------------------------------------------------------------------
-- 4. transport_item 运输对象明细（托板上的 N 件物料）
--    位置信息只在这里，order 不冗余 source/target —— 这是多起点多终点的前提
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_item` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `item_no`                varchar(64)   NOT NULL COMMENT '明细编号(单内唯一)',
    `item_no_guard`          varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `item_no`, NULL)) VIRTUAL COMMENT '生成列，逻辑删除后释放编号',
    `order_id`               bigint        NOT NULL COMMENT '所属运输单编号',
    `request_id`             bigint        NULL COMMENT '来源运输需求编号',
    `load_cycle_no`          int           NOT NULL DEFAULT 1 COMMENT '装载轮次(第几轮装上车的)',
    `material_instance_id`   bigint        NULL COMMENT 'WMS物料实例编号',
    `load_unit_id`           bigint        NULL COMMENT 'WMS载具编号(托板由 AGV 自带时为空)',
    `load_unit_code`         varchar(128)  NULL COMMENT '载具条码快照',
    `batch_no`               varchar(64)   NULL COMMENT '业务批次号',
    `quantity`               decimal(18,6) NOT NULL DEFAULT 1 COMMENT '运输数量',
    `source_slot_id`         bigint        NOT NULL COMMENT '起点槽位编号',
    `source_slot_code`       varchar(64)   NOT NULL COMMENT '起点槽位编码快照',
    `target_slot_id`         bigint        NOT NULL COMMENT '目标槽位编号',
    `target_slot_code`       varchar(64)   NOT NULL COMMENT '目标槽位编码快照',
    `current_slot_id`        bigint        NULL COMMENT '当前槽位只读快照，真相源在 WMS',
    `current_slot_code`      varchar(64)   NULL COMMENT '当前槽位编码快照',
    `transport_load_slot_id` bigint        NULL COMMENT '托板上的载位编号(AGV 第几个位)',
    `pickup_stop_id`         bigint        NULL COMMENT '取料停靠点编号',
    `dropoff_stop_id`        bigint        NULL COMMENT '放料停靠点编号',
    `mix_group`              varchar(64)   NULL COMMENT '混载兼容组',
    `sequence_no`            int           NOT NULL DEFAULT 0 COMMENT '装载顺序 1..N',
    `status`                 varchar(32)   NOT NULL DEFAULT 'WAITING' COMMENT '状态: WAITING/ON_AGV/DELIVERED/FAILED',
    `last_movement_id`       bigint        NULL COMMENT '最近一次WMS移动流水编号(交付时写入)',
    `loaded_at`              datetime      NULL COMMENT '实际装载时间',
    `unloaded_at`            datetime      NULL COMMENT '实际卸载时间',
    `version`                int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_item_no` (`tenant_id`, `order_id`, `item_no_guard`),
    UNIQUE KEY `uk_transport_item_seq` (`tenant_id`, `order_id`, `sequence_no`),
    KEY `idx_transport_item_order` (`order_id`, `status`, `sequence_no`),
    KEY `idx_transport_item_material` (`material_instance_id`),
    KEY `idx_transport_item_slot` (`current_slot_id`, `status`),
    KEY `idx_transport_item_batch` (`tenant_id`, `batch_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输对象明细';

-- ----------------------------------------------------------------------------
-- 5. transport_stop 停靠点（位置序列）
--    PICKUP: 每轮装载一条，stop_seq = cycle_no * 10
--    DROPOFF: V1 只有一条，stop_seq = 900；多终点时 900/910/920...
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_stop` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `order_id`               bigint        NOT NULL COMMENT '所属运输单编号',
    `stop_seq`               int           NOT NULL COMMENT '停靠顺序: PICKUP 10/20/30，DROPOFF 900/910',
    `cycle_no`               int           NULL COMMENT '装载轮次(PICKUP 专用)',
    `stop_type`              varchar(16)   NOT NULL COMMENT '停靠职责: PICKUP/DROPOFF',
    `location_type`          varchar(32)   NOT NULL COMMENT '位置类型: STORAGE/MACHINE/BUFFER/DOOR',
    `location_id`            bigint        NULL COMMENT '业务位置编号',
    `location_code`          varchar(64)   NULL COMMENT '业务位置编码',
    `transfer_slot_id`       bigint        NULL COMMENT 'WMS槽位编号',
    `interface_device_id`    bigint        NULL COMMENT '门禁/机台等交互设备编号',
    `planned_arrival_at`     datetime      NULL COMMENT '预计到达时间',
    `actual_arrival_at`      datetime      NULL COMMENT '实际到达时间',
    `planned_departure_at`   datetime      NULL COMMENT '预计离开时间',
    `actual_departure_at`    datetime      NULL COMMENT '实际离开时间',
    `wait_timeout_sec`       int           NULL COMMENT '本站最大等待秒数',
    `condition_json`         json          NULL COMMENT '到达/开门/取放料条件快照',
    `status`                 varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/TRAVELLING/ARRIVED/OPERATING/COMPLETED/SKIPPED/FAILED',
    `version`                int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_stop_seq` (`tenant_id`, `order_id`, `stop_seq`, `deleted`),
    KEY `idx_transport_stop_status` (`order_id`, `status`),
    KEY `idx_transport_stop_slot` (`transfer_slot_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport停靠点';

-- ----------------------------------------------------------------------------
-- 6. transport_operation 原子动作（命令台账）
--    定位: 记录「发了什么命令、结果如何」，不做独立状态机推进
--    状态推进、重试、超时全部由 Flow 节点负责
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_operation` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `operation_no`           varchar(64)   NOT NULL COMMENT '动作编号',
    `operation_no_guard`     varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `operation_no`, NULL)) VIRTUAL COMMENT '生成列',
    `order_id`               bigint        NOT NULL COMMENT '所属运输单编号',
    `stop_id`                bigint        NULL COMMENT '所属停靠点编号',
    `item_id`                bigint        NULL COMMENT '关联运输对象编号',
    `cycle_no`               int           NULL COMMENT '装载轮次',
    `operation_seq`          int           NOT NULL COMMENT '单内动作顺序 10/20/30',
    `operation_type`         varchar(32)   NOT NULL COMMENT '动作类型: MOVE/GRAB/PLACE/OPEN_DOOR/CLOSE_DOOR/VERIFY/UPDATE_LOCATION/RELEASE_SLOT',
    `executor_type`          varchar(32)   NULL COMMENT '执行器类型: AGV/ROBOT_ARM/MACHINE/DOOR/WMS',
    `executor_id`            bigint        NULL COMMENT '执行器设备编号',
    `device_command_id`      bigint        NULL COMMENT 'Device命令编号',
    `source_slot_id`         bigint        NULL COMMENT '动作源槽位',
    `target_slot_id`         bigint        NULL COMMENT '动作目标槽位',
    `depends_on`             varchar(512)  NULL COMMENT '依赖动作编号列表(逗号分隔)',
    `request_payload`        json          NULL COMMENT '动作请求参数快照',
    `response_payload`       json          NULL COMMENT '动作结果快照',
    `idempotency_key`        varchar(128)  NOT NULL COMMENT '动作幂等键 {orderNo}:{cycle}:{action}',
    `status`                 varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/SUCCEEDED/FAILED/SKIPPED',
    `attempt`                int           NOT NULL DEFAULT 0 COMMENT '当前尝试次数',
    `max_attempts`           int           NOT NULL DEFAULT 3 COMMENT '最大尝试次数',
    `timeout_at`             datetime      NULL COMMENT '动作超时时间',
    `started_at`             datetime      NULL COMMENT '开始时间',
    `finished_at`            datetime      NULL COMMENT '完成时间',
    `error_code`             varchar(64)   NULL COMMENT '错误编码',
    `error_message`          varchar(512)  NULL COMMENT '错误说明',
    `version`                int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_operation_no` (`tenant_id`, `operation_no_guard`),
    UNIQUE KEY `uk_transport_operation_idempotent` (`tenant_id`, `idempotency_key`),
    KEY `idx_transport_operation_order` (`order_id`, `stop_id`, `operation_seq`),
    KEY `idx_transport_operation_status` (`status`, `timeout_at`),
    KEY `idx_transport_operation_command` (`device_command_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport原子动作(命令台账)';

-- ----------------------------------------------------------------------------
-- 7. transport_local_lock 本地资源锁
--    resource_type 收缩为: EXECUTOR/DOOR/MACHINE_INTERFACE/CHARGER
--    槽位类资源不走这里，归 WMS 的 TransportWmsApi
--    过期回收: 无后台 sweeper，acquire 时惰性清理 expire_at < now 的记录
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_local_lock` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `resource_type`          varchar(32)   NOT NULL COMMENT '资源类型: EXECUTOR/DOOR/MACHINE_INTERFACE/CHARGER',
    `resource_key`           varchar(128)  NOT NULL COMMENT '资源唯一键(推荐用ID而非编码)',
    `order_id`               bigint        NOT NULL COMMENT '持锁运输单编号',
    `lock_token`             varchar(128)  NOT NULL COMMENT '随机锁令牌',
    `fencing_token`          bigint        NOT NULL DEFAULT 0 COMMENT '单调递增防旧令牌(取 MAX+1，不用 nanoTime)',
    `status`                 varchar(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/RELEASED/EXPIRED',
    `active_guard`           varchar(160)  GENERATED ALWAYS AS (IF(`deleted` = b'0' AND `status` = 'ACTIVE', CONCAT(`resource_type`, '#', `resource_key`), NULL)) VIRTUAL COMMENT '生成列，同租户同资源仅一条有效锁',
    `acquired_at`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    `expire_at`              datetime      NOT NULL COMMENT '租约过期时间(应 >= 对应 Flow 节点超时)',
    `heartbeat_at`           datetime      NULL COMMENT '最近续租时间',
    `released_at`            datetime      NULL COMMENT '释放时间',
    `release_reason`         varchar(64)   NULL COMMENT '释放原因: COMPLETED/FAILED/CANCELLED/EXPIRED',
    `version`                int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_local_lock_active` (`tenant_id`, `active_guard`),
    UNIQUE KEY `uk_transport_local_lock_token` (`tenant_id`, `lock_token`),
    KEY `idx_transport_local_lock_order` (`order_id`, `status`),
    KEY `idx_transport_local_lock_expire` (`status`, `expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport本地资源锁';

-- ----------------------------------------------------------------------------
-- 8. transport_lock_ref 运输单与锁的关联（业务维度：这单占了哪些资源）
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_lock_ref` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `order_id`               bigint        NOT NULL COMMENT '运输单编号',
    `stop_id`                bigint        NULL COMMENT '停靠点编号',
    `operation_id`           bigint        NULL COMMENT '原子动作编号',
    `lock_mode`              varchar(16)   NOT NULL DEFAULT 'LOCAL' COMMENT '锁来源: LOCAL/RESOURCE',
    `lock_ref`               varchar(128)  NOT NULL COMMENT '本地锁ID或外部租约ID',
    `resource_type`          varchar(32)   NOT NULL COMMENT '资源类型: EXECUTOR/DOOR/MACHINE_INTERFACE/CHARGER',
    `resource_key`           varchar(128)  NOT NULL COMMENT '资源唯一键',
    `scope_type`             varchar(16)   NOT NULL COMMENT '持有范围: ORDER/STOP/OPERATION',
    `fencing_token`          bigint        NULL COMMENT '防止旧持有者操作的递增令牌',
    `status`                 varchar(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/RELEASED/EXPIRED',
    `acquired_at`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
    `expire_at`              datetime      NULL COMMENT '租约过期时间',
    `released_at`            datetime      NULL COMMENT '释放时间',
    `release_reason`         varchar(64)   NULL COMMENT '释放原因',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_transport_lock_ref_order` (`order_id`, `status`),
    KEY `idx_transport_lock_ref_resource` (`tenant_id`, `resource_type`, `resource_key`, `status`),
    KEY `idx_transport_lock_ref_expire` (`status`, `expire_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输锁关联';

-- ----------------------------------------------------------------------------
-- 9. transport_handover_session 双边交接会话
--    托板模式: 一趟一次交接；多件物料的移动流水逐条记在 transport_item.last_movement_id
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_handover_session` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `handover_no`            varchar(64)   NOT NULL COMMENT '交接会话编号',
    `handover_no_guard`      varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `handover_no`, NULL)) VIRTUAL COMMENT '生成列',
    `order_id`               bigint        NOT NULL COMMENT '所属运输单编号',
    `stop_id`                bigint        NOT NULL COMMENT '所属停靠点编号',
    `operation_id`           bigint        NULL COMMENT '对应原子动作编号(PLACE)',
    `item_id`                bigint        NULL COMMENT '主物料编号(多件时仅作参考，明细见evidence)',
    `handover_type`          varchar(32)   NOT NULL COMMENT '交接类型: STORAGE_TO_TRANSPORT/TRANSPORT_TO_STORAGE/TRANSPORT_TO_MACHINE',
    `from_slot_id`           bigint        NULL COMMENT '交出方槽位(托板模式下可为空)',
    `to_slot_id`             bigint        NOT NULL COMMENT '接收方槽位',
    `provider_device_id`     bigint        NULL COMMENT '交出方设备编号(AGV)',
    `receiver_device_id`     bigint        NULL COMMENT '接收方设备编号(仓储/机台)',
    `provider_status`        varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '交出方确认状态',
    `receiver_status`        varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT '接收方确认状态',
    `status`                 varchar(24)   NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED/VERIFYING/CONFIRMED/FAILED',
    `movement_batch_no`      varchar(64)   NULL COMMENT 'WMS移动流水批次号(逐条流水见item.last_movement_id)',
    `verified_at`            datetime      NULL COMMENT '证据比对通过时间(可重放 commit 的依据)',
    `committed_at`           datetime      NULL COMMENT 'WMS落库完成时间',
    `started_at`             datetime      NULL COMMENT '交接开始时间',
    `confirmed_at`           datetime      NULL COMMENT '双边确认时间',
    `timeout_at`             datetime      NULL COMMENT '交接超时时间',
    `failure_code`           varchar(64)   NULL COMMENT '失败编码',
    `failure_message`        varchar(512)  NULL COMMENT '失败说明',
    `version`                int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_handover_no` (`tenant_id`, `handover_no_guard`),
    KEY `idx_transport_handover_order` (`order_id`, `stop_id`),
    KEY `idx_transport_handover_status` (`status`, `timeout_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport双边交接会话';

-- ----------------------------------------------------------------------------
-- 10. transport_handover_evidence 交接证据（只增不改）
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_handover_evidence` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `session_id`             bigint        NOT NULL COMMENT '交接会话编号',
    `evidence_side`          varchar(16)   NOT NULL COMMENT '证据侧: PROVIDER/RECEIVER/OBSERVER',
    `evidence_type`          varchar(32)   NOT NULL COMMENT '证据类型: LOAD_SENSOR/SLOT_SENSOR/BARCODE/RFID/VISION/PLATE_POSITION',
    `device_id`              bigint        NULL COMMENT '证据来源设备编号',
    `device_command_id`      bigint        NULL COMMENT '来源Device命令编号',
    `expected_value`         varchar(512)  NULL COMMENT '期望值',
    `actual_value`           varchar(512)  NULL COMMENT '实际值',
    `matched`                bit(1)        NULL COMMENT '是否匹配',
    `evidence_payload`       json          NULL COMMENT '完整原始证据报文',
    `collected_at`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '证据采集时间',
    `valid`                  bit(1)        NOT NULL DEFAULT b'1' COMMENT '证据是否有效',
    `invalid_reason`         varchar(255)  NULL COMMENT '证据无效原因',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_transport_evidence_session` (`session_id`, `collected_at`),
    KEY `idx_transport_evidence_device` (`device_id`, `device_command_id`),
    KEY `idx_transport_evidence_type` (`evidence_type`, `valid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport交接证据(只增不改)';

-- ----------------------------------------------------------------------------
-- 11. transport_event 运输事件流水（只增不改，兼 Outbox）
-- ----------------------------------------------------------------------------
CREATE TABLE `transport_event` (
    `id`                     bigint        NOT NULL COMMENT '主键(雪花)',
    `event_no`               varchar(64)   NOT NULL COMMENT '事件编号',
    `event_no_guard`         varchar(64)   GENERATED ALWAYS AS (IF(`deleted` = b'0', `event_no`, NULL)) VIRTUAL COMMENT '生成列',
    `request_id`             bigint        NULL COMMENT '运输需求编号',
    `order_id`               bigint        NULL COMMENT '运输单编号',
    `stop_id`                bigint        NULL COMMENT '停靠点编号',
    `operation_id`           bigint        NULL COMMENT '动作编号',
    `item_id`                bigint        NULL COMMENT '运输对象编号',
    `handover_id`            bigint        NULL COMMENT '交接会话编号',
    `event_type`             varchar(64)   NOT NULL COMMENT '事件类型: REQUEST_CREATED/ORDER_DISPATCHED/ITEM_LOADED/SLOT_RELEASED/HANDOVER_CONFIRMED/WMS_COMMITTED/ORDER_COMPLETED 等',
    `before_status`          varchar(32)   NULL COMMENT '变更前状态',
    `after_status`           varchar(32)   NULL COMMENT '变更后状态',
    `event_payload`          json          NULL COMMENT '事件内容快照',
    `source_type`            varchar(32)   NULL COMMENT '来源类型: TRANSPORT/DEVICE/WMS/FLOW/MANUAL',
    `source_ref`             varchar(128)  NULL COMMENT '来源引用',
    `occurred_at`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务发生时间',
    `published`              bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否已发布Outbox事件',
    `published_at`           datetime      NULL COMMENT '发布时间',
    `trace_id`               varchar(64)   NULL COMMENT '全链路追踪ID',
    `idempotency_key`        varchar(128)  NULL COMMENT '事件幂等键',
    `creator`                varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`            datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_transport_event_no` (`tenant_id`, `event_no_guard`),
    UNIQUE KEY `uk_transport_event_idempotent` (`tenant_id`, `idempotency_key`),
    KEY `idx_transport_event_order` (`order_id`, `occurred_at`),
    KEY `idx_transport_event_publish` (`published`, `occurred_at`),
    KEY `idx_transport_event_type` (`event_type`, `occurred_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Transport运输事件流水(只增不改)';

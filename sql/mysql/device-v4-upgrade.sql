-- =============================================================================
-- 设备域 v4 增量迁移（对已入库的 9 张表升到 v4；权威 DDL 以 device.sql 为准）
-- 目标库: boheng-frame (MySQL 8.0+)
-- 前置: 已执行过 device.sql（v1.0-device，9 张表）
-- 设计正文: .workbuddy/reference/device-domain-architecture.md（v4）
-- 对标结论: .workbuddy/reference/device-design-prior-art-librax.md
-- =============================================================================
--
-- 本次变更（5 项）:
--   A. device_info   +4 列：busy_check_policy / concurrency_policy / max_inflight / inflight_count
--      → ★ 忙闲校验与并发策略必须有开关：仪器不一定报空闲状态，部分仪器自带本地队列
--   B. device_action +5 列：need_busy_check / status_command_code / request_template /
--                          poll_done_expr / poll_max_times
--      → 报文模板化、轮询判定配置化、下发前忙闲预检
--   C. device_command 重建：新增 11 列 + 1 改名（end_time→finished_at）+ 删 4 列、唯一键 2→3、device_id 改可空
--   D. 新增 device_codec（9 → 10 张表）
--   E. 删除字段：ref_type / ref_id（被业务定位三列 source_type/business_exec_id/node_id 取代）、
--      start_time（≡ create_time）、callback_deadline（被通用 timeout_at 收口）；end_time 改名为 finished_at
--
-- ⚠️ 安全性：device_info / device_action / device_property / device_param_set 用 ALTER 保留数据；
--   device_command 本次为**空表**，采用 DROP + CREATE（结构改动过大，重建更干净）。
--   若 device_command 已有生产数据，请改用逐列 ALTER，**不要**执行本脚本的 DROP。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- A. device_info：忙闲校验与并发策略（★★ 用户明确要求：不得写死）
-- -----------------------------------------------------------------------------
ALTER TABLE `device_info`
    ADD COLUMN `busy_check_policy`  varchar(32) NOT NULL DEFAULT 'AUTO'
        COMMENT '★忙闲校验总开关: AUTO 按动作 need_busy_check 决定/ALWAYS 下发前强制预检/NEVER 从不预检(仪器不报空闲状态时用此项)' AFTER `poll_interval_sec`,
    ADD COLUMN `concurrency_policy` varchar(32) NOT NULL DEFAULT 'EXCLUSIVE'
        COMMENT '★并发策略: EXCLUSIVE 平台独占(一次仅一条在途，台账CAS)/DEVICE_QUEUED 仪器自带本地队列(平台可连续下发，不做独占)/PLATFORM_QUEUED 平台侧排队(入库等空闲后依次下发)' AFTER `busy_check_policy`,
    ADD COLUMN `max_inflight`       int         NOT NULL DEFAULT 1
        COMMENT '允许同时在途命令数(EXCLUSIVE 恒为 1；DEVICE_QUEUED 取仪器本地队列深度；PLATFORM_QUEUED 一般 1)' AFTER `concurrency_policy`,
    ADD COLUMN `inflight_count`     int         NOT NULL DEFAULT 0
        COMMENT '当前在途命令数(运行态；与 max_inflight 比较决定能否继续下发)' AFTER `max_inflight`;

-- -----------------------------------------------------------------------------
-- B. device_action：下发与结果回收（报文模板 / 轮询判定 / 忙闲预检）
-- -----------------------------------------------------------------------------
ALTER TABLE `device_action`
    ADD COLUMN `need_busy_check`     bit(1)       NOT NULL DEFAULT b'0'
        COMMENT '★本动作下发前是否需先确认设备空闲(最终是否真预检还要看 device_info.busy_check_policy；设备不支持则自动降级为直接下发)' AFTER `estimate_duration_ms`,
    ADD COLUMN `status_command_code` varchar(64)  NULL
        COMMENT '忙闲查询动作编码(need_busy_check=1 时用；指向同类型的另一动作；为空则降级为不预检)' AFTER `need_busy_check`,
    ADD COLUMN `request_template`    text         NULL
        COMMENT '请求报文模板(支持 ${param} 占位符；渲染结果即 device_command.payload_json，可复现)' AFTER `status_command_code`,
    ADD COLUMN `poll_done_expr`      varchar(255) NULL
        COMMENT '轮询完成判定表达式(如 $.status == "DONE"；result_mode=POLL 时用)' AFTER `request_template`,
    ADD COLUMN `poll_max_times`      int          NULL
        COMMENT '最大轮询次数(超限判 TIMED_OUT；为空则走全局默认)' AFTER `poll_done_expr`;

-- -----------------------------------------------------------------------------
-- C. device_command：重建（⚠️ 仅当为空表时执行 DROP）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `device_command`;
CREATE TABLE `device_command` (
    `id`                bigint        NOT NULL COMMENT '主键(雪花)=commandNo',
    `command_no`        varchar(64)   NOT NULL COMMENT '命令编号(对外返回的 commandNo；可与 id 相同或业务规则生成)',
    -- ★ 业务定位（幂等作用域必须含业务维度，否则重试被吞）----------------------
    `source_type`       varchar(32)   NULL COMMENT '★调用来源: FLOW 调度流程/MANUAL 手工/API 外部调用/DEVICE 设备自触发/INTERNAL 内部调用',
    `business_exec_id`  varchar(64)   NULL COMMENT '★上游业务执行标识(流程场景=flow executionId；直接执行=execId；为空即「不做业务幂等」，NULL 不参与唯一)',
    `node_id`           varchar(64)   NULL COMMENT '★上游节点标识(流程节点编码；直接执行固定 direct；非流程内部调用用明确业务节点编码)',
    `attempt`           int           NOT NULL DEFAULT 1 COMMENT '★同一业务节点的第几次尝试(从 1 起；每次重试必须是新记录，否则调度域看不到第 2 次执行)',
    -- 设备（先落事实再抢设备 → CREATED 阶段可空）--------------------------------
    `device_id`         bigint        NULL COMMENT '设备编号(device_info.id)；★CREATED 阶段为空：抢不到设备也要留痕，资源缺口才可量化',
    `device_code`       varchar(64)   NULL COMMENT '设备编码(冗余，编码寻址)；CREATED 阶段为空',
    `device_type_code`  varchar(64)   NULL COMMENT '设备类型编码(快照)',
    `action_code`       varchar(64)   NOT NULL COMMENT '动作编码',
    `param_set_id`      bigint        NULL COMMENT '参数集编号(可空=直接传参不套参数集)',
    -- 三态调用两维度 -----------------------------------------------------------
    `dispatch_mode`     varchar(16)   NOT NULL DEFAULT 'ASYNC' COMMENT '下发方式: SYNC 同步/ASYNC 异步（⚠️与「忙闲预检」无关，预检见 device_action.need_busy_check）',
    `result_mode`       varchar(16)   NOT NULL DEFAULT 'POLL' COMMENT '回收方式: SYNC_RETURN 同步返回/CALLBACK 回调/POLL 轮询',
    `poll_count`        int           NOT NULL DEFAULT 0 COMMENT '已轮询次数(用于判断是否达 poll_max_times)',
    `next_poll_time`    datetime      NULL COMMENT '下次轮询时间(POLL 调度用)',
    -- ★ 等待原因（与 status 正交：status 说「走到哪」，waiting_for 说「在等什么」）--
    `waiting_for`       varchar(32)   NULL COMMENT '★等待原因: SLOT_AVAILABLE 等空闲设备/DEVICE_CALLBACK 等回调/POLL_TICK 等下次轮询/MANUAL_APPROVE 等人工审批/EXTERNAL_EVENT 等外部事件',
    -- 状态机（先落事实再抢设备）-------------------------------------------------
    `status`            varchar(32)   NOT NULL DEFAULT 'CREATED' COMMENT '状态机: CREATED 已创建(未选设备)/ACQUIRED 已抢占设备/SENT 已下发/WAITING 等结果(看 waiting_for)/SUCCEEDED 成功/FAILED 失败/TIMED_OUT 超时/CANCELLED 取消',
    `device_job_id`     varchar(128)  NULL COMMENT '设备侧作业编号(驱动返回的 vendor job id；仅用于轮询与物理取消，**不作幂等键**)',
    -- 进度（observable 命令）---------------------------------------------------
    `progress_percent`  int           NULL COMMENT '进度百分比 0-100',
    `current_step`      varchar(128)  NULL COMMENT '当前步骤(如 孵育/读取/回位)',
    `step_total`        int           NULL COMMENT '总步骤数',
    `eta_sec`           int           NULL COMMENT '预计剩余时间(秒)',
    -- 参数与报文（快照 + 渲染后 payload，可复现）-------------------------------
    `params_json`       json          NULL COMMENT '参数快照(下发那一刻的参数，含默认值展开)',
    `payload_json`      json          NULL COMMENT '渲染后 payload(驱动真正发出去的报文，可复现)',
    `request_json`      json          NULL COMMENT '请求原文(排障生命线)',
    `response_json`     json          NULL COMMENT '响应原文(排障生命线)',
    `error_msg`         varchar(1024) NULL COMMENT '错误信息(失败/超时时写仪器原文)',
    `recovery_action`   varchar(512)  NULL COMMENT '失败时的可操作恢复动作(如 重试/检查耗材/联系工程师)',
    -- 结果引用（L0 原始数据）--------------------------------------------------
    `codec_code`        varchar(64)   NULL COMMENT '响应解析规则编码(device_codec.codec_code；为空则原始响应直接入 L0，不解析)',
    `data_raw_id`       bigint        NULL COMMENT '关联 L0 原始数据编号(device_data_raw.id)',
    -- 幂等（三键分工中的前两键；第三键 uk_business 由上面三个业务字段组成）--------
    `idempotent_key`    varchar(128)  NULL COMMENT '幂等键(拦截 MQTT QoS1/2 重投与 HTTP 重发；为空不参与唯一约束)',
    `callback_token_hash` varchar(64) NULL COMMENT '★回调令牌 SHA-256 摘要(result_mode=CALLBACK 时必填)；**禁止存明文**；没有它任何人 POST 就能灌伪造测量数据',
    -- 追溯与操作 --------------------------------------------------------------
    `operator`          varchar(64)   NULL COMMENT '操作人(手工=登录用户；调度/设备=system 或设备编码)',
    `operator_type`     varchar(16)   NOT NULL DEFAULT 'USER' COMMENT '操作者类型: USER 人工/DEVICE 设备/AUTO 自动',
    -- ★ 四个时间戳（缺一就分不清「排队多久」与「设备跑多久」）---------------------
    `acquired_at`       datetime      NULL COMMENT '★抢占设备成功时间(与 sent_at 之差 = 排队等待耗时)',
    `sent_at`           datetime      NULL COMMENT '★报文实际发出时间(与 finished_at 之差 = 设备执行耗时)',
    `finished_at`       datetime      NULL COMMENT '进入终态的时间',
    `timeout_at`        datetime      NULL COMMENT '★本次执行超时截止时刻(超时回收任务按此扫描；CALLBACK 到期先降级 POLL 兜一次再判)',
    `version`           int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次状态流转递增)',
    `remark`            varchar(512)  NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`           varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除(命令只增不改，业务侧不提供删除入口)',
    `tenant_id`         bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_command_no` (`command_no`),
    UNIQUE KEY `uk_idempotent` (`tenant_id`, `idempotent_key`),
    UNIQUE KEY `uk_business` (`tenant_id`, `source_type`, `business_exec_id`, `node_id`, `attempt`),
    KEY `idx_device` (`device_id`, `create_time`),
    KEY `idx_status` (`status`, `timeout_at`),
    KEY `idx_next_poll` (`next_poll_time`),
    KEY `idx_business` (`business_exec_id`, `node_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备命令(只增不改；三态调用统一落点；状态机+等待原因+进度+幂等三键)';

-- -----------------------------------------------------------------------------
-- D. device_codec：新增（响应解析规则）
--   ⚠️ 一期只开放 JSON / REGEX；SCRIPT 存 Groovy/JS = 代码注入面，建字段不开放。
--   ⚠️ 单位换算与有效范围刻意不放本表（属 L1 标准化层，避免把解析与标准化焊死）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device_codec` (
    `id`             bigint        NOT NULL COMMENT '主键(雪花)',
    `codec_code`     varchar(64)   NOT NULL COMMENT '解析规则编码(如 TECAN_READER_RESULT_V1)',
    `codec_code_key` varchar(64)   GENERATED ALWAYS AS (IF(deleted = b'0', `codec_code`, NULL)) VIRTUAL COMMENT '生成列(逻辑删除后释放编码)',
    `codec_name`     varchar(128)  NOT NULL COMMENT '解析规则名称',
    `parse_type`     varchar(32)   NOT NULL DEFAULT 'JSON' COMMENT '解析类型: JSON 字段映射/REGEX 正则捕获(一期仅此两种可用)',
    `field_mapping`  json          NULL COMMENT '字段映射(JSON 对象: 输出字段名 -> JSONPath 或 group:N 正则捕获组)',
    `regex_pattern`  varchar(1024) NULL COMMENT '正则表达式(parse_type=REGEX 时使用)',
    `sample_raw`     text          NULL COMMENT '样例原始报文(供前端调试解析规则，避免盲写)',
    `status`         tinyint       NOT NULL DEFAULT 0 COMMENT '启用状态: 0 启用/1 停用',
    `remark`         varchar(512)  NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`        varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`      bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_codec_code` (`tenant_id`, `codec_code_key`),
    KEY `idx_parse_type` (`parse_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备响应解析规则(L0->L1 解析；一期仅 JSON/REGEX)';

-- =============================================================================
-- 执行后自检（手动跑）:
--   SELECT table_name FROM information_schema.tables
--    WHERE table_schema='boheng-frame' AND table_name LIKE 'device%';   -- 应为 10 张
--   SHOW CREATE TABLE `boheng-frame`.device_command;                     -- 应有 uk_business
--   SELECT device_code, busy_check_policy, concurrency_policy, max_inflight
--     FROM `boheng-frame`.device_info;                                   -- 存量 2 行应为 AUTO/EXCLUSIVE/1
-- =============================================================================

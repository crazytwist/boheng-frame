-- =============================================================================
-- Lab-Boheng · 设备域（Device Domain）一期 DDL
-- 版本: v2.0-device (v4 架构)   底座: boheng-frame (cn.boheng.frame)   目标库: MySQL 8.0+
-- 依赖: 无（全新域，10 张表；错误码段位 1-004-000-000）
-- 设计正文: .workbuddy/reference/device-domain-architecture.md（v4）
-- 对标结论: .workbuddy/reference/device-design-prior-art-librax.md
-- =============================================================================
--
-- 设计要点（写 DDL 必须对齐的约定）:
--   1. 双模块双 SPI：boheng-module-device-api（纯契约）+ boheng-module-device（实现）。
--      本 DDL 属实现侧 DAL，表前缀 device_（与 wms_ 同构）。
--   2. 平台只认两种协议：HTTP + MQTT；其余协议经 Node-RED 收敛为这两种之一。
--      connection_type 决定走哪条 Transport——★ 传输层由 device 模块自定义 SPI（DeviceTransport），
--      外部网关（芋道 iot-gateway 等）只作为「可选适配器模块」接入，主模块零依赖。
--   3. 三种调用方式 = 同一个命令的两个正交维度（不是三个接口）:
--        dispatch_mode（SYNC/ASYNC 下发方式）× result_mode（SYNC_RETURN/CALLBACK/POLL 回收方式）
--      三态共用 device_command 一张表、一个状态机、一套幂等键、一套锁。
--   4. ★ status 与 waiting_for 正交：status 只表达「流程走到哪一步」（设备侧事实），
--      waiting_for 表达「在等什么」（SLOT_AVAILABLE/DEVICE_CALLBACK/POLL_TICK/MANUAL_APPROVE/EXTERNAL_EVENT）。
--      严禁把「等审批」「等定时」混进 status。
--   5. ★★ 幂等三键分工（缺一不可）:
--        uk_command_no  对外唯一（调用方拿到的凭据）
--        uk_idempotent  传输重投拦截（MQTT QoS1/2、HTTP 重发）
--        uk_business    业务执行事实（tenant_id, source_type, business_exec_id, node_id, attempt）
--      只用单键会把「重试」拦成「返回原命令」→ 调度域永远看不到第 2 次执行 → 重试等于没重试。
--   6. ★★ 忙闲校验与并发策略**必须有开关**：不是所有仪器都返回空闲状态，部分仪器自带本地队列。
--      device_info.busy_check_policy（AUTO/ALWAYS/NEVER）× device_action.need_busy_check
--      × device_info.concurrency_policy（EXCLUSIVE/DEVICE_QUEUED/PLATFORM_QUEUED）—— 三处都不得写死。
--   7. 生命周期**先落事实再抢设备**：CREATED（已落库、device_id 可为空）→ ACQUIRED → SENT → WAITING → 终态。
--      抢不到设备也必须留痕，否则「资源缺口」无法量化且崩溃不可恢复。
--   8. 数据分三层（术语对齐 TetraScience / Medallion）:
--        L0 device_data_raw（不可变）→ L1 device_measurement(+_data)（摄入时挂上下文）→ L2 device_analysis_result（可重判）
--   9. 设备占用锁对齐 SiLA Lock Controller：lock_holder/lock_type/lock_expire_time/lock_reason。
--  10. 框架审计字段（与 wms_* 一致，租户表继承 TenantBaseDO）:
--        creator / create_time / updater / update_time / deleted(bit 默认 b'0') / tenant_id
--  11. 主键一律雪花（@TableId(type = IdType.ASSIGN_ID)），DDL 里不设 auto_increment。
--  12. 唯一键用「生成列 + 唯一索引」模式兼容逻辑删除（业务编码类字段）。
--
-- ⚠️ 术语表（**必读，两套系统对 command 用了相反语义**）:
--      boheng: device_action   = 配置（这台设备能被怎么启动）
--              device_command  = 记录（一次执行的完整事实，只增不改）
--      librax: lab_device_command = 配置；lab_device_execution = 记录
--      → 对外沟通/文档/迁移脚本一律按 boheng 口径，禁止混用。
-- ⚠️ 撞名消歧：device_command.dispatch_mode = SYNC/ASYNC（下发方式）；
--      「下发前要不要查忙闲」本 DDL 命名为 device_action.need_busy_check + device_info.busy_check_policy，
--      **不叫 dispatch_mode**（librax 用了这个名，必须避开以免二期对接踩坑）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. device_info  设备台账（一台物理设备一行；也是「按条件选设备」与「占用锁」的主表）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_info` (
    `id`                  bigint       NOT NULL COMMENT '主键(雪花)',
    `device_code`         varchar(64)  NOT NULL COMMENT '设备编码(业务唯一，跨模块寻址只认编码)',
    `device_code_key`     varchar(64)  GENERATED ALWAYS AS (IF(deleted = b'0', `device_code`, NULL)) VIRTUAL COMMENT '生成列(逻辑删除后释放编码，NULL 不参与唯一)',
    `device_name`         varchar(128) NOT NULL COMMENT '设备名称',
    `image_url`           varchar(512) NULL COMMENT '设备图片地址(可空，前端展示默认图；走 infra 文件上传)',
    `device_type_code`    varchar(64)  NOT NULL COMMENT '设备类型编码(行为建模，不按型号；如 PLATE_READER)',
    `model`               varchar(128) NULL COMMENT '型号(如 Infinite 200 PRO)',
    `vendor`              varchar(128) NULL COMMENT '厂商(如 Tecan)',
    `serial_no`           varchar(128) NULL COMMENT '序列号',
    `driver_type`         varchar(64)  NOT NULL COMMENT '驱动类型(决定走哪个 DeviceDriver；如 TECAN_READER_NETWORK / SIMULATED)',
    `status`              varchar(32)  NOT NULL DEFAULT 'OFFLINE' COMMENT '在线状态: ONLINE 在线/OFFLINE 离线/MAINTENANCE 维护中/FAULT 故障',
    -- 接入字段 ----------------------------------------------------------------
    `connection_type`     varchar(32)  NOT NULL DEFAULT 'HTTP' COMMENT '接入方式: HTTP 直连/MQTT 直连/NODE_RED 经Node-RED/SIMULATED 仿真',
    `endpoint_url`        varchar(512) NULL COMMENT '接入地址(HTTP=base url；MQTT=broker host:port；NODE_RED=Node-RED http 入站节点 url)',
    `login_path`          varchar(255) NULL COMMENT '登录相对路径(如 /api/login)。与 endpoint_url 拼接；空表示调用前不登录',
    `login_method`        varchar(16)  NULL COMMENT '登录 HTTP 方法，默认 POST',
    `login_username`      varchar(128) NULL COMMENT '登录用户名',
    `login_password`      varchar(255) NULL COMMENT '登录密码',
    `login_username_key`  varchar(64)  NULL COMMENT '登录报文里用户名的字段名，默认 username',
    `login_password_key`  varchar(64)  NULL COMMENT '登录报文里密码的字段名，默认 password',
    `token_path`          varchar(128) NULL COMMENT '登录响应里 token 的 JSON 路径，如 token 或 data.accessToken',
    `token_header`        varchar(64)  NULL COMMENT '携带 token 的请求头，默认 Authorization',
    `token_prefix`        varchar(32)  NULL COMMENT 'token 前缀，默认 Bearer',
    `token_ttl_sec`       int          NULL COMMENT 'token 缓存秒数，到期后重新登录',
    `mqtt_topic_prefix`   varchar(128) NULL COMMENT 'MQTT 主题前缀(如 device/{code}/)，直连 MQTT 时使用',
    `callback_enabled`    bit(1)       NOT NULL DEFAULT b'0' COMMENT '仪器能否主动推送结果(决定 result_mode 用 CALLBACK 还是 POLL)',
    `poll_interval_sec`   int          NULL COMMENT '默认轮询间隔(秒)，POLL 方式与遥测批量刷新共用',
    -- ★★ 忙闲校验与并发策略（v4 新增；三类仪器行为不同，必须可配，禁止写死）------
    `busy_check_policy`   varchar(32)  NOT NULL DEFAULT 'AUTO' COMMENT '★忙闲校验总开关: AUTO 按动作 need_busy_check 决定/ALWAYS 下发前强制预检/NEVER 从不预检(仪器不报空闲状态时用此项)',
    `concurrency_policy`  varchar(32)  NOT NULL DEFAULT 'EXCLUSIVE' COMMENT '★并发策略: EXCLUSIVE 平台独占(一次仅一条在途，台账CAS)/DEVICE_QUEUED 仪器自带本地队列(平台可连续下发，不做独占)/PLATFORM_QUEUED 平台侧排队(入库等空闲后依次下发)',
    `max_inflight`        int          NOT NULL DEFAULT 1 COMMENT '允许同时在途命令数(EXCLUSIVE 恒为 1；DEVICE_QUEUED 取仪器本地队列深度；PLATFORM_QUEUED 一般 1)',
    `inflight_count`      int          NOT NULL DEFAULT 0 COMMENT '当前在途命令数(运行态；与 max_inflight 比较决定能否继续下发)',
    -- 能力自描述 / 遥测 / 仿真 --------------------------------------------------
    `capability_source`   varchar(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '能力来源: DEVICE_DECLARED 驱动上报/MANUAL 手工(可覆盖)',
    `telemetry_json`      json         NULL COMMENT '属性当前值快照(列表页与按状态选设备用；详细历史二期进 device_status_log)',
    `simulation_mode`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否仿真模式(驱动按此分派到 SimulatedDriver)',
    `discovery_type`      varchar(32)  NULL COMMENT '发现方式(一期手工录设备，字段预留 mDNS/Zeroconf 等)',
    -- 占用锁（对齐 SiLA Lock Controller）--------------------------------------
    `current_command_id`  bigint       NULL COMMENT '当前占用命令编号(指向 device_command.id；EXCLUSIVE 模式下的 CAS 抢占判断依据)',
    `lock_holder`         varchar(64)  NULL COMMENT '锁持有方(command_no / USER:1 / TASK:xxx)',
    `lock_type`           varchar(32)  NULL COMMENT '锁类型: COMMAND 命令占用/MANUAL 人工/MANUAL_MAINTENANCE 维护锁',
    `lock_acquired_time`  datetime     NULL COMMENT '锁获取时间',
    `lock_expire_time`    datetime     NULL COMMENT '锁过期时间(锁必须能过期，否则设备永久锁死)',
    `lock_reason`         varchar(255) NULL COMMENT '加锁原因(人工/维护加锁时必填)',
    `remark`              varchar(512) NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`             varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`           bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_device_code` (`tenant_id`, `device_code_key`),
    KEY `idx_type` (`device_type_code`),
    KEY `idx_status` (`status`),
    KEY `idx_driver` (`driver_type`),
    KEY `idx_connection` (`connection_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备台账(设备管理与占用锁主表)';

-- -----------------------------------------------------------------------------
-- 2. device_action  设备动作定义（「能被怎么启动」；param_schema 是参数下发核心）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_action` (
    `id`                   bigint        NOT NULL COMMENT '主键(雪花)',
    `device_type_code`     varchar(64)   NOT NULL COMMENT '设备类型编码(动作归属的设备类型)',
    `action_code`          varchar(64)   NOT NULL COMMENT '动作编码(如 READ_PLATE / OPEN_DOOR / STOP)',
    `action_code_key`      varchar(64)   GENERATED ALWAYS AS (IF(deleted = b'0', CONCAT(`device_type_code`, '#', `action_code`), NULL)) VIRTUAL COMMENT '生成列(类型#动作，逻辑删除后释放)',
    `action_name`          varchar(128)  NOT NULL COMMENT '动作名称(如 读板/开门/急停)',
    `standard_feature`     varchar(128)  NULL COMMENT '映射 SiLA 标准特性名(如 com.sila_standard.run_control；私有动作留空)',
    `param_schema`         json          NULL COMMENT '参数模式: {参数名:{label,type,unit,required,default,constraints,source}}（含默认值+约束+单位，前端按 schema 动态渲染表单）',
    `vendor_ref`           varchar(255)  NULL COMMENT '厂商映射(i-control 脚本名/方法名/协议命令)',
    `source`               varchar(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '能力来源: DEVICE_DECLARED 驱动上报/MANUAL 手工(可覆盖)',
    `estimate_duration_ms` bigint        NULL COMMENT '预估耗时(毫秒)；< 3000 才允许 dispatch_mode=SYNC，长命令同步必超时',
    -- ★ 下发与结果回收（v4 吸收 librax：报文模板化 + 轮询判定配置化 + 忙闲预检）----
    `need_busy_check`      bit(1)        NOT NULL DEFAULT b'0' COMMENT '★本动作下发前是否需先确认设备空闲(最终是否真预检还要看 device_info.busy_check_policy；设备不支持则自动降级为直接下发)',
    `status_command_code`  varchar(64)   NULL COMMENT '忙闲查询动作编码(need_busy_check=1 时用；指向同类型的另一动作；为空则降级为不预检)',
    `http_method`          varchar(16)   NULL COMMENT 'HTTP 方法: GET/POST/PUT/PATCH/DELETE。与台账 endpoint_url 拼接时使用；非 HTTP 接入可空',
    `body_format`          varchar(16)   NULL COMMENT '请求报文格式: JSON/FORM 表单/TEXT 纯文本/XML',
    `request_path`         varchar(255)  NULL COMMENT '接口相对路径(如 /api/read)。主机在 device_info.endpoint_url，动作按设备类型共用，不写完整地址',
    `request_template`     text          NULL COMMENT '请求报文模板(支持 ${param} 占位符；渲染结果即 device_command.payload_json，可复现)',
    `codec_code`           varchar(64)   NULL COMMENT '响应解析规则编码(device_codec.codec_code；调用成功后把响应解析成测量)',
    `poll_done_expr`       varchar(255)  NULL COMMENT '轮询完成判定表达式(如 $.status == "DONE"；result_mode=POLL 时用)',
    `poll_max_times`       int           NULL COMMENT '最大轮询次数(超限判 TIMED_OUT；为空则走全局默认)',
    `status`               tinyint       NOT NULL DEFAULT 0 COMMENT '启用状态: 0 启用/1 停用',
    -- 框架字段 ----------------------------------------------------------------
    `creator`              varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`            bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_type_action` (`tenant_id`, `action_code_key`),
    KEY `idx_standard_feature` (`standard_feature`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备动作定义(启动方式+参数模式，SiLA 能力自描述落点)';

-- -----------------------------------------------------------------------------
-- 3. device_property  设备可观测属性定义（遥测；v2 补上的「半个 SiLA」）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_property` (
    `id`                bigint        NOT NULL COMMENT '主键(雪花)',
    `device_type_code`  varchar(64)   NOT NULL COMMENT '设备类型编码',
    `property_code`     varchar(64)   NOT NULL COMMENT '属性编码(如 TEMPERATURE / DOOR_STATE / CONSUMABLE_REMAIN)',
    `property_code_key` varchar(64)   GENERATED ALWAYS AS (IF(deleted = b'0', CONCAT(`device_type_code`, '#', `property_code`), NULL)) VIRTUAL COMMENT '生成列(类型#属性)',
    `property_name`     varchar(128)  NOT NULL COMMENT '属性名称(如 温度/仓门状态/耗材余量)',
    `data_type`         varchar(32)   NOT NULL DEFAULT 'STRING' COMMENT '数据类型: STRING/INTEGER/DECIMAL/BOOLEAN/ENUM/JSON',
    `unit`              varchar(32)   NULL COMMENT '单位(如 ℃ / % )',
    `readable`          bit(1)        NOT NULL DEFAULT b'1' COMMENT '是否可读',
    `subscribable`      bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否可订阅(仪器支持推送时置 1)',
    `poll_interval_sec` int           NULL COMMENT '轮询间隔(秒)；不订阅的属性按此批量刷新当前值',
    `source`            varchar(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '能力来源: DEVICE_DECLARED/MANUAL',
    `status`            tinyint       NOT NULL DEFAULT 0 COMMENT '启用状态: 0 启用/1 停用',
    -- 框架字段 ----------------------------------------------------------------
    `creator`           varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_type_property` (`tenant_id`, `property_code_key`),
    KEY `idx_subscribable` (`subscribable`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备可观测属性定义(遥测)';

-- -----------------------------------------------------------------------------
-- 4. device_param_set  参数集预设（「这一次下什么参数」的可复用模板）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_param_set` (
    `id`                bigint       NOT NULL COMMENT '主键(雪花)',
    `param_set_code`    varchar(64)  NOT NULL COMMENT '参数集编码',
    `param_set_code_key` varchar(64) GENERATED ALWAYS AS (IF(deleted = b'0', `param_set_code`, NULL)) VIRTUAL COMMENT '生成列(逻辑删除后释放编码)',
    `param_set_name`    varchar(128) NOT NULL COMMENT '参数集名称',
    `device_type_code`  varchar(64)  NOT NULL COMMENT '设备类型编码',
    `action_code`       varchar(64)  NOT NULL COMMENT '关联动作编码',
    `params_json`       json         NOT NULL COMMENT '参数取值(与 device_action.param_schema 对齐)',
    `validated`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否已验证(只有 validated 才能被发起命令)',
    `validated_by`      varchar(64)  NULL COMMENT '验证人',
    `validated_time`    datetime     NULL COMMENT '验证时间',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '启用状态: 0 启用/1 停用',
    -- 框架字段 ----------------------------------------------------------------
    `creator`           varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`         bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_param_set_code` (`tenant_id`, `param_set_code_key`),
    KEY `idx_type_action` (`device_type_code`, `action_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '参数集预设(已验证的可复用下发参数)';

-- -----------------------------------------------------------------------------
-- 5. device_codec  响应解析规则（v4 新增；把仪器原始报文转成统一字段）
--   ⚠️ 一期只开放 JSON / REGEX。HEX 按位解析与 SCRIPT（Groovy/JS）**建字段但不开放**：
--      script_content 存脚本 = 代码注入面（要沙箱 + 类白名单 + 超时 + 禁 IO/反射），收益远低于成本。
--   ⚠️ 单位换算(unit_conversions)与有效范围(valid_range) **刻意不放本表** —— 它们属 L1 标准化层，
--      塞进解析层会把「解析」与「标准化」焊死，解析器升级就无法重算 L1。
-- -----------------------------------------------------------------------------
CREATE TABLE `device_codec` (
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

-- -----------------------------------------------------------------------------
-- 6. device_command  命令（只增不改；三态调用统一落点；核心表）
--   ★ 幂等三键分工 / 状态与等待原因正交 / 四个时间戳 / 乐观锁
-- -----------------------------------------------------------------------------
CREATE TABLE `device_command` (
    `id`                bigint        NOT NULL COMMENT '主键(雪花)=commandNo',
    `command_no`        varchar(64)   NOT NULL COMMENT '命令编号(对外返回的 commandNo；可与 id 相同或业务规则生成)',
    -- ★ 业务定位（v4 吸收：幂等作用域必须含业务维度，否则重试被吞）---------------
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
    -- 三态调用两维度（v3 核心）------------------------------------------------
    `dispatch_mode`     varchar(16)   NOT NULL DEFAULT 'ASYNC' COMMENT '下发方式: SYNC 同步/ASYNC 异步（⚠️与「忙闲预检」无关，预检见 device_action.need_busy_check）',
    `result_mode`       varchar(16)   NOT NULL DEFAULT 'POLL' COMMENT '回收方式: SYNC_RETURN 同步返回/CALLBACK 回调/POLL 轮询',
    `poll_count`        int           NOT NULL DEFAULT 0 COMMENT '已轮询次数(用于判断是否达 poll_max_times)',
    `next_poll_time`    datetime      NULL COMMENT '下次轮询时间(POLL 调度用)',
    -- ★ 等待原因（与 status 正交：status 说「走到哪」，waiting_for 说「在等什么」）--
    `waiting_for`       varchar(32)   NULL COMMENT '★等待原因: SLOT_AVAILABLE 等空闲设备/DEVICE_CALLBACK 等回调/POLL_TICK 等下次轮询/MANUAL_APPROVE 等人工审批/EXTERNAL_EVENT 等外部事件',
    -- 状态机（v4 生命周期：先落事实再抢设备）------------------------------------
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
-- 7. device_data_raw  L0 原始数据（不可变；解析器升级后可重建 L1/L2）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_data_raw` (
    `id`                bigint         NOT NULL COMMENT '主键(雪花)',
    `command_id`        bigint         NOT NULL COMMENT '关联命令编号(device_command.id)',
    `device_id`         bigint         NOT NULL COMMENT '设备编号',
    `device_code`       varchar(64)    NOT NULL COMMENT '设备编码(冗余)',
    `data_type`         varchar(32)    NOT NULL COMMENT '原始数据类型: HTTP_RESPONSE 报文/MQTT_MESSAGE 消息/FILE 文件',
    `raw_json`          json           NULL COMMENT '原始报文(HTTP/MQTT 直取时存此)',
    `file_url`          varchar(512)   NULL COMMENT '文件引用(大结果转 infra 文件服务，如 S3/SFTP；存 url)',
    `file_size`         bigint         NULL COMMENT '文件大小(字节)',
    `file_sha256`       varchar(64)    NULL COMMENT '文件校验值(SHA256，防篡改/去重)',
    `mime_type`         varchar(64)    NULL COMMENT 'MIME 类型(如 application/vnd.ms-excel)',
    `format`            varchar(32)    NULL COMMENT '结果格式(如 XML/EXCEL/CSV/JSON)',
    `received_time`     datetime       NOT NULL COMMENT '接收时间(摄入时打点，不可变)',
    `remark`            varchar(512)   NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`           varchar(64)    NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           varchar(64)    NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           bit(1)         NOT NULL DEFAULT b'0' COMMENT '是否删除(L0 不可变，业务侧不提供删除入口)',
    `tenant_id`         bigint         NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_command` (`command_id`),
    KEY `idx_device` (`device_id`, `received_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备原始数据(L0 raw，不可变，可重建 L1/L2)';

-- -----------------------------------------------------------------------------
-- 8. device_measurement  L1 测量头（一次测量一行；上下文必须在摄入时挂上）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_measurement` (
    `id`                    bigint        NOT NULL COMMENT '主键(雪花)',
    `command_id`            bigint        NOT NULL COMMENT '关联命令编号',
    `data_raw_id`           bigint        NOT NULL COMMENT '关联 L0 原始数据编号',
    `device_id`             bigint        NOT NULL COMMENT '设备编号',
    `device_code`           varchar(64)   NOT NULL COMMENT '设备编码(快照)',
    `driver_version`        varchar(64)   NULL COMMENT '驱动版本(快照；驱动升级后行为会变，必须留)',
    -- ★ L1 摄入时必填上下文（TetraScience 铁律：context attached at ingestion，事后不可补）--
    `plate_instance_id`     bigint        NULL COMMENT '板实例编号(板会移动/消耗/销毁，必须摄入时写)',
    `plate_instance_code`   varchar(64)   NULL COMMENT '板实例编码',
    `plate_type_code`       varchar(64)   NULL COMMENT '板型编码(快照)',
    `device_labware_name`   varchar(128)  NULL COMMENT '仪器板型名(下发快照，如 Tecan 板型映射点)',
    `experiment_ref`        varchar(64)   NULL COMMENT '实验引用(来自调度时)',
    `task_ref`              varchar(64)   NULL COMMENT '任务引用(来自调度时)',
    `script_name`           varchar(128)  NULL COMMENT '脚本/方法名(快照，方法会被改)',
    `method_params_json`    json          NULL COMMENT '方法关键参数快照',
    `operator`              varchar(64)   NULL COMMENT '操作人',
    `operator_type`         varchar(16)   NULL COMMENT '操作者类型',
    -- 测量元信息 --------------------------------------------------------------
    `measure_mode`          varchar(64)   NULL COMMENT '检测模式(如 吸收光/荧光/发光)',
    `wavelength_nm`         int           NULL COMMENT '主波长(nm)',
    `read_time`             datetime      NULL COMMENT '读数时间(仪器侧时间，支持补录)',
    `remark`                varchar(512)  NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`               varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_command` (`command_id`),
    KEY `idx_device` (`device_id`, `read_time`),
    KEY `idx_plate` (`plate_instance_id`, `read_time`),
    KEY `idx_experiment` (`experiment_ref`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备测量头(L1 standardized，摄入时挂上下文)';

-- -----------------------------------------------------------------------------
-- 9. device_measurement_data  L1 孔级读数（行级可查，不做合并）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_measurement_data` (
    `id`              bigint         NOT NULL COMMENT '主键(雪花)',
    `measurement_id`  bigint         NOT NULL COMMENT '关联测量头编号(device_measurement.id)',
    `well_position`   varchar(16)    NOT NULL COMMENT '孔位坐标(如 A1 / H12)',
    `row_index`       smallint       NULL COMMENT '行号(1 起)',
    `col_index`       smallint       NULL COMMENT '列号(1 起)',
    `wavelength_nm`   int            NULL COMMENT '波长(nm)；多波长时同一孔多行',
    `read_value`      decimal(20,6)  NULL COMMENT '读数值(原始 OD/荧光值)',
    `unit`            varchar(32)    NULL COMMENT '单位',
    `raw_text`        varchar(128)   NULL COMMENT '原始文本(解析异常时保留原值，便于回溯)',
    `quality_flag`    varchar(32)    NULL COMMENT '质量标记(如 OVER_RANGE 超出量程/BLANK_FAIL 空白异常；L2 基础标记)',
    `remark`          varchar(255)   NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`         varchar(64)    NULL DEFAULT '' COMMENT '创建者',
    `create_time`     datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)    NULL DEFAULT '' COMMENT '更新者',
    `update_time`     datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)         NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`       bigint         NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_measurement` (`measurement_id`, `wavelength_nm`),
    KEY `idx_measurement_well` (`measurement_id`, `well_position`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备孔级读数(L1 standardized 明细；96孔×波长=行级可查)';

-- -----------------------------------------------------------------------------
-- 10. device_analysis_result  L2 分析结论/标签（带规则版本，可重判）
-- -----------------------------------------------------------------------------
CREATE TABLE `device_analysis_result` (
    `id`              bigint        NOT NULL COMMENT '主键(雪花)',
    `measurement_id`  bigint        NOT NULL COMMENT '关联测量头编号',
    `device_id`       bigint        NOT NULL COMMENT '设备编号',
    `result_type`     varchar(64)   NOT NULL COMMENT '结论类型(如 QC_PASS 质控通过/POSITIVE 阳性/OUTLIER 异常)',
    `result_value`    varchar(255)  NULL COMMENT '结论值',
    `rule_code`       varchar(64)   NULL COMMENT '规则编码(可重判的依据)',
    `rule_version`    varchar(32)   NULL COMMENT '规则版本(规则升级后可重判历史并对比新旧)',
    `source_system`   varchar(32)   NOT NULL DEFAULT 'INTERNAL' COMMENT '来源: INTERNAL 自算/MAGELLAN 等外部分析软件',
    `confidence`      decimal(5,4)  NULL COMMENT '置信度 0-1',
    `summary_json`    json          NULL COMMENT '结论摘要(结构化)',
    `remark`          varchar(512)  NULL COMMENT '备注',
    -- 框架字段 ----------------------------------------------------------------
    `creator`         varchar(64)   NULL DEFAULT '' COMMENT '创建者',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)   NULL DEFAULT '' COMMENT '更新者',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`       bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_measurement` (`measurement_id`),
    KEY `idx_rule` (`rule_code`, `rule_version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '设备分析结论(L2 analytic；带规则版本可重判)';

-- =============================================================================
-- 附：配套 DDL 变更（对既有表，需与 WMS TODO G 区联动；本期一并列出，单独脚本执行）
-- =============================================================================
-- G1. wms_container_type 加 device_labware_name（容器类型 → 仪器板型名对照）
--     ALTER TABLE `wms_container_type` ADD COLUMN `device_labware_name` varchar(128) NULL
--         COMMENT '仪器板型名(如 Tecan i-control 板型映射点；上机前校验用)' AFTER `position_naming`;
-- G2. wms_slot_info.device_code 升级为强引用 + 删除校验（DEVICE_HAS_SLOTS）
--     错误码段位：设备域 1-004-000-000（infra 1-001 / system 1-002 / wms 1-003 已占）
--     ⚠️ 与 deleteZone 留悬空 zone_code 是同一个坑，删除设备前必须校验无关联槽位。
-- =============================================================================

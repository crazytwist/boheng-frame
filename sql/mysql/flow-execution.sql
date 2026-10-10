-- =============================================================================
-- 实验室自动执行 · 流程运行层 v3
-- 五张表：运行、节点执行、信号收件箱、分项上下文、分支决策。
-- 设计要点：
--   1. 运行绑死发布版本 + 指纹（checksum），防定义漂移；
--   2. 节点执行「一次尝试一行」：attempt 递增不覆盖，iteration_no 区分循环轮次；
--   3. skip_reason 三值：BRANCH（出口未选中，汇合视为满足）/ FAILURE（前置失败）/ UPSTREAM（上游连带）；
--   4. 信号收件箱按 signal_id 幂等去重，设备回调 / 人工 / 定时 / 子流程终态统一入口；
--   5. 唯一约束统一用「生成列 + 唯一索引」：逻辑删除后生成列置 NULL，释放业务键；
--   6. 子流程扇出的子运行用 parent_* 四元组挂回父节点（FANOUT 幂等建行靠 business_key 唯一）。
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `flow_execution` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `execution_no`          varchar(64)  NOT NULL COMMENT '运行业务号，如 exe_20261010_...。对外展示和日志检索用',
    `execution_no_guard`    varchar(64)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `execution_no`, NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放业务号',
    `pipeline_key`          varchar(64)  NOT NULL COMMENT '绑定的流程编码（启动时冻结）',
    `version`               int          NOT NULL COMMENT '绑定的流程版本（启动时冻结）',
    `definition_id`         bigint       NOT NULL COMMENT '指向 flow_definition.id',
    `checksum`              varchar(64)  NULL COMMENT '启动时冻结的定义指纹，检测漂移',
    `status`                varchar(32)  NOT NULL COMMENT 'PENDING 待启动 / RUNNING 进行中 / SUCCESS 成功 / FAILED 失败 / CANCELLED 已取消',
    `trigger_type`          varchar(32)  NOT NULL COMMENT '触发方式：MANUAL 手动 / SCHEDULE 定时 / CHILD 子流程',
    `triggered_by`          varchar(64)  NULL COMMENT '触发人',
    `business_key`          varchar(64)  NULL COMMENT '业务关联键，如实验单号。FANOUT 子运行 = {父executionId}:{父nodeId}:{attempt}:{groupIndex}',
    `business_key_guard`    varchar(140) GENERATED ALWAYS AS (IF(`deleted` = b'0', CONCAT(`parent_execution_id`, '#', `parent_node_id`, '#', `parent_attempt`, '#', `business_key`), NULL)) VIRTUAL COMMENT '生成列。FANOUT 幂等：同一父节点同一尝试下 business_key 唯一',
    `parent_execution_id`   bigint       NULL COMMENT '父运行编号（子流程场景）。顶层运行为空',
    `parent_node_id`        varchar(32)  NULL COMMENT '父运行里启动本子运行的节点编码',
    `parent_attempt`        int          NULL COMMENT '父节点的第几次尝试。CHILD 信号按它回传',
    `parent_group_index`    int          NULL COMMENT 'FANOUT 扇出的第几组。SINGLE 为空',
    `input_params`          json         NULL COMMENT '启动入参 JSON',
    `output_data`           json         NULL COMMENT '运行产出 JSON。全部节点完成时汇总',
    `started_at`            datetime     NULL COMMENT '启动时间',
    `finished_at`           datetime     NULL COMMENT '终态时间',
    `error_code`            varchar(64)  NULL COMMENT '失败码',
    `error_message`         varchar(512) NULL COMMENT '失败说明',
    `row_version`           int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_execution_no` (`tenant_id`, `execution_no_guard`),
    UNIQUE KEY `uk_flow_execution_fanout` (`tenant_id`, `business_key_guard`),
    KEY `idx_flow_execution_pipeline` (`pipeline_key`, `status`),
    KEY `idx_flow_execution_business` (`business_key`),
    KEY `idx_flow_execution_parent` (`parent_execution_id`, `parent_node_id`, `parent_attempt`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '一次流程运行。子流程扇出的子运行用 parent_* 挂回父节点';

CREATE TABLE IF NOT EXISTS `flow_step_execution` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `execution_id`          bigint       NOT NULL COMMENT '所属运行',
    `node_id`               varchar(32)  NOT NULL COMMENT '节点编码',
    `step_type`             varchar(64)  NULL COMMENT '启动时冻结的步骤类型 key',
    `iteration_no`          int          NOT NULL DEFAULT 0 COMMENT '循环轮次，从 0 开始。非循环节点恒为 0',
    `attempt`               int          NOT NULL DEFAULT 1 COMMENT '本轮的尝试序号，从 1 开始。重试新行不覆盖',
    `row_key`               varchar(96)  GENERATED ALWAYS AS (IF(`deleted` = b'0', CONCAT(`execution_id`, '#', `node_id`, '#', `iteration_no`, '#', `attempt`), NULL)) VIRTUAL COMMENT '生成列。运行内 节点+轮次+尝试 唯一',
    `status`                varchar(32)  NOT NULL COMMENT 'PENDING 待执行 / RUNNING 执行中 / WAITING 等外部 / SUCCESS 成功 / FAILED 失败可重试 / DEAD 失败且次数用尽 / SKIPPED 被裁剪 / CANCELLED 已取消',
    `skip_reason`           varchar(32)  NULL COMMENT '仅 SKIPPED：BRANCH 出口未选中 / FAILURE 前置失败 / UPSTREAM 上游连带',
    `waiting_for`           varchar(32)  NULL COMMENT '等什么：DEVICE_CALLBACK / MANUAL / TIMER / CHILD。仅 WAITING 时有值',
    `wait_ref`              varchar(128) NULL COMMENT '等待引用，如设备命令号、到期时刻',
    `command_id`            bigint       NULL COMMENT '关联的设备命令编号（device.command 专用）',
    `command_no`            varchar(64)  NULL COMMENT '关联的设备命令号',
    `selected_branch`       varchar(32)  NULL COMMENT '路由节点选中的出口',
    `input_snapshot`        json         NULL COMMENT '输入快照 JSON（参数映射解析后的值）',
    `output_data`           json         NULL COMMENT '已结算的输出 JSON',
    `error_code`            varchar(64)  NULL COMMENT '失败码',
    `error_message`         varchar(512) NULL COMMENT '失败说明',
    `queued_at`             datetime     NULL COMMENT '入队时间',
    `started_at`            datetime     NULL COMMENT '开始执行时间',
    `finished_at`           datetime     NULL COMMENT '终态时间',
    `deadline_at`           datetime     NULL COMMENT '等待截止时刻。超时扫描按它找',
    `row_version`           int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_step_execution` (`tenant_id`, `row_key`),
    KEY `idx_flow_step_execution_exec` (`execution_id`),
    KEY `idx_flow_step_execution_waiting` (`execution_id`, `status`, `waiting_for`),
    KEY `idx_flow_step_execution_deadline` (`deadline_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '一次节点执行尝试。重试新行不覆盖，循环按轮次展开';

CREATE TABLE IF NOT EXISTS `flow_step_signal` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `signal_id`             varchar(128) NOT NULL COMMENT '来源方幂等 ID。同一信号重发不重复消费',
    `signal_id_guard`       varchar(128) GENERATED ALWAYS AS (IF(`deleted` = b'0', `signal_id`, NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放信号 ID',
    `execution_id`          bigint       NOT NULL COMMENT '目标运行',
    `node_id`               varchar(32)  NOT NULL COMMENT '目标节点',
    `attempt`               int          NOT NULL COMMENT '目标尝试序号',
    `iteration_no`          int          NOT NULL DEFAULT 0 COMMENT '循环轮次',
    `signal_type`           varchar(32)  NOT NULL COMMENT 'MANUAL / TIMER / CHILD / EXTERNAL',
    `payload`               json         NULL COMMENT '信号内容 JSON。人工备注、子运行结果等',
    `success`               bit(1)       NULL COMMENT '信号结论：1 放行 / 0 拒绝（人工否决）',
    `process_status`        varchar(32)  NOT NULL COMMENT 'RECEIVED 已收 / APPLIED 已消费 / DUPLICATE 重复 / IGNORED 无目标 / REJECTED 已拒绝',
    `received_at`           datetime     NULL COMMENT '收到时间',
    `processed_at`          datetime     NULL COMMENT '消费时间',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_step_signal` (`tenant_id`, `signal_id_guard`),
    KEY `idx_flow_step_signal_target` (`execution_id`, `node_id`, `process_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '统一信号收件箱。幂等去重靠 signal_id 唯一键';

CREATE TABLE IF NOT EXISTS `flow_execution_context` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `execution_id`          bigint       NOT NULL COMMENT '所属运行',
    `namespace`             varchar(16)  NOT NULL COMMENT 'INPUT 启动入参 / NODE 节点输出 / SYSTEM 系统变量',
    `node_id`               varchar(32)  NULL COMMENT 'NODE 命名空间下的节点编码；其它命名空间为空',
    `iteration_no`          int          NOT NULL DEFAULT 0 COMMENT '循环轮次。NODE 命名空间下区分各轮输出',
    `context_key`           varchar(64)  NOT NULL COMMENT '上下文键',
    `ctx_row_key`           varchar(160) GENERATED ALWAYS AS (IF(`deleted` = b'0', CONCAT(`execution_id`, '#', `namespace`, '#', IFNULL(`node_id`, ''), '#', `iteration_no`, '#', `context_key`), NULL)) VIRTUAL COMMENT '生成列。运行内 命名空间+节点+轮次+键 唯一',
    `value_data`            json         NULL COMMENT '值 JSON',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_execution_context` (`tenant_id`, `ctx_row_key`),
    KEY `idx_flow_execution_context_exec` (`execution_id`, `namespace`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '分项执行上下文。路由表达式、循环条件、参数映射都从这里取值';

CREATE TABLE IF NOT EXISTS `flow_branch_decision` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `execution_id`          bigint       NOT NULL COMMENT '所属运行',
    `route_node_id`         varchar(32)  NOT NULL COMMENT '路由节点编码',
    `attempt`               int          NOT NULL COMMENT '节点的尝试序号',
    `iteration_no`          int          NOT NULL DEFAULT 0 COMMENT '循环轮次',
    `selected_branch`       varchar(32)  NOT NULL COMMENT '选中的出口名',
    `match_mode`            varchar(16)  NULL COMMENT '匹配语义：EXCLUSIVE / INCLUSIVE',
    `activated_targets`     json         NULL COMMENT '被激活的目标节点编码 JSON 数组',
    `decision_data`         json         NULL COMMENT '决策快照 JSON：表达式文本 + 求值时的变量值',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_flow_branch_decision_exec` (`execution_id`, `route_node_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'route 节点的分支决策记录。事后要能回答「这次为什么走了这条路」';

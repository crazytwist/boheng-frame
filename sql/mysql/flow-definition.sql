-- =============================================================================
-- 实验室自动执行 · 流程定义 v3
-- 三张表：可复用步骤（能力目录）、流程版本、流程节点。
-- v3 关键变化：
--   1. 步骤类型开放：flow_step.step_type + type_config 取代 device_code/action_code，
--      新增类型（脚本/人工/通知/Bean…）零改表；
--   2. flow_node 放开自由连线：depends_on 是任意 DAG，sort_no 只管展示；
--   3. 控制类节点（route/loop/subflow/timer.wait）直接在节点上带 step_type + type_config，
--      step_id 允许为空；
--   4. 编排字段：join_policy / join_failure_policy / branches / output_mapping / run_config。
-- 运行层五张表见 flow-execution.sql。错误码段：1-005-000-000。
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `flow_definition` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `pipeline_key`          varchar(64)  NOT NULL COMMENT '流程编码。创建后不可改',
    `version`               int          NOT NULL COMMENT '版本号，从 1 递增',
    `version_key`           varchar(80)  GENERATED ALWAYS AS (IF(`deleted` = b'0', CONCAT(`pipeline_key`, '#', `version`), NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放编码加版本',
    `name`                  varchar(128) NOT NULL COMMENT '流程名称',
    `remark`                varchar(512) NULL COMMENT '说明',
    `status`                varchar(32)  NOT NULL COMMENT 'DRAFT 草稿 / PUBLISHED 已发布 / RETIRED 已停用',
    `checksum`              varchar(64)  NULL COMMENT '发布时节点图的 SHA-256。草稿为空',
    `published_at`          datetime     NULL COMMENT '发布时间',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_definition_version` (`tenant_id`, `version_key`),
    KEY `idx_flow_definition_key` (`pipeline_key`),
    KEY `idx_flow_definition_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '实验流程版本。已发布的版本不可再改';

CREATE TABLE IF NOT EXISTS `flow_step` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `step_key`              varchar(32)  NOT NULL COMMENT '步骤编码。创建后不可改，如 spin、read',
    `step_key_guard`        varchar(32)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `step_key`, NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放步骤编码',
    `name`                  varchar(128) NOT NULL COMMENT '步骤名称',
    `step_type`             varchar(64)  NOT NULL COMMENT '步骤类型 key：device.command / bean.invoke / script.run / manual.task / notification.send。控制类类型不允许入目录',
    `type_config`           json         NULL COMMENT '类型专属配置 JSON。device.command 存 {"deviceCode":..,"actionCode":..}',
    `default_params`        json         NULL COMMENT '通用默认输入 JSON。节点引用时可覆盖',
    `timeout_ms`            bigint       NOT NULL COMMENT '这一步最多等待的毫秒数',
    `max_attempts`          int          NOT NULL COMMENT '含首次。当前运行时尚未执行',
    `remark`                varchar(512) NULL COMMENT '说明',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '0 启用，可以被流程引用；1 停用',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_step` (`tenant_id`, `step_key_guard`),
    KEY `idx_flow_step_type` (`step_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '可复用实验步骤（能力目录）。一种能力 = step_type + type_config';

CREATE TABLE IF NOT EXISTS `flow_node` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `definition_id`         bigint       NOT NULL COMMENT '所属流程版本',
    `step_id`               bigint       NULL COMMENT '引用的能力（flow_step.id）。控制类节点为空',
    `node_id`               varchar(32)  NOT NULL COMMENT '这个版本里的节点编码。引用能力时默认同步骤编码',
    `node_key`              varchar(96)  GENERATED ALWAYS AS (IF(`deleted` = b'0', CONCAT(`definition_id`, '#', `node_id`), NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放节点编码',
    `name`                  varchar(128) NULL COMMENT '节点显示名。控制类节点必填；引用能力时为空，展示取步骤名称',
    `sort_no`               int          NOT NULL COMMENT '直线顺序，从 0 开始。只作展示排序，依赖的真相源是 depends_on',
    `depends_on`            json         NOT NULL COMMENT '前置节点编码数组。空数组 = 起点；多前置 = 汇合；被多个节点依赖 = 扇出并行',
    `step_type`             varchar(64)  NULL COMMENT '节点级类型覆盖。空则取 flow_step.step_type；控制类节点必填',
    `type_config`           json         NULL COMMENT '节点级类型配置 JSON。引用能力时为空（用 flow_step.type_config），控制类节点必填',
    `input_mapping`         json         NULL COMMENT '这一次引用的参数映射。来源 INPUT / NODE / LITERAL',
    `output_mapping`        json         NULL COMMENT '节点输出归一化到上下文的映射：{输出字段: 上下文键}',
    `run_config`            json         NULL COMMENT '节点级运行覆盖：{"timeoutMs":..,"maxAttempts":..,"failureStrategy":"FAIL_FAST"}',
    `join_policy`           varchar(32)  NULL COMMENT '汇合策略：ALL_SUCCESS 全部成功（默认）/ ANY_SUCCESS 任一成功 / ALL_DONE 全部终态',
    `join_failure_policy`   varchar(32)  NULL COMMENT '前置失败策略：FAIL 跟着失败（默认）/ SKIP 裁剪本节点',
    `branches`              json         NULL COMMENT '控制类节点的出口映射（route 专用）：{"pass":["nodeId"],"default":["nodeId"]}',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_node` (`tenant_id`, `node_key`),
    KEY `idx_flow_node_definition` (`definition_id`),
    KEY `idx_flow_node_step` (`step_id`),
    KEY `idx_flow_node_type` (`step_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程节点。能力引用与控制类节点统一在这里，编排形态（依赖/汇合/分支）都落在本行';

-- =============================================================================
-- 实验室自动执行 · 流程定义（第一步）
-- 三张表：步骤定义、流程版本、流程和步骤的关联。运行表等运行时再加。
-- 已经按旧脚本建过 flow_node 的库，再执行 flow-step.sql 做拆分。
-- 错误码段：1-005-000-000
-- 设计说明：.workbuddy/reference/flow-v2.md
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
    `device_code`           varchar(64)  NOT NULL COMMENT '台账上的设备编码',
    `action_code`           varchar(64)  NOT NULL COMMENT '该设备类型下的动作编码',
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
    UNIQUE KEY `uk_flow_step` (`tenant_id`, `step_key_guard`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '实验步骤。可被多条流程引用';

CREATE TABLE IF NOT EXISTS `flow_node` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `definition_id`         bigint       NOT NULL COMMENT '所属流程版本',
    `step_id`               bigint       NOT NULL COMMENT '关联的步骤。设备和动作在步骤表上',
    `node_id`               varchar(32)  NOT NULL COMMENT '这个版本里的节点编码。默认同步骤编码，同一步用两次时要改成别的',
    `node_key`              varchar(96)  GENERATED ALWAYS AS (IF(`deleted` = b'0', CONCAT(`definition_id`, '#', `node_id`), NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放节点编码',
    `sort_no`               int          NOT NULL COMMENT '直线顺序，从 0 开始。服务端按这个顺序写依赖',
    `depends_on`            json         NOT NULL COMMENT '前置节点编码数组。第一步是 []，后面每步只依赖紧挨着的上一步',
    `input_mapping`         json         NULL COMMENT '这一次引用的参数映射。来源 INPUT / NODE / LITERAL',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_node` (`tenant_id`, `node_key`),
    KEY `idx_flow_node_definition` (`definition_id`),
    KEY `idx_flow_node_step` (`step_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程和步骤的关联。顺序和依赖在这里，设备和动作在步骤表';

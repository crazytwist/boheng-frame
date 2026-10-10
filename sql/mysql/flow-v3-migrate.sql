-- =============================================================================
-- 流程定义 v2 → v3 迁移
-- 适用：已经按旧版 flow-definition.sql / flow-step.sql 建过表的库。
-- 新库直接执行 flow-definition.sql + flow-execution.sql，不要跑本脚本。
--
-- v3 关键变化：
--   1. flow_step 的 device_code/action_code 收进 type_config（step_type + type_config 开放类型）；
--   2. flow_node.step_id 允许为空（控制类节点自带 step_type + type_config）；
--   3. flow_node 新增编排字段：name / output_mapping / run_config / join_policy / join_failure_policy / branches；
--   4. depends_on 语义放开：任意 DAG，不再由顺序重写。
--
-- 迁移步骤：
--   ① 步骤表：补 step_type/type_config/default_params，把设备动作收进 JSON，再删旧列；
--   ② 节点表：补新列，step_id 放开非空；
--   ③ 运行层五张表：直接执行 flow-execution.sql。
-- 可重复执行前的幂等性由各 DDL 的 IF NOT EXISTS / 手工判断保证；
-- 本脚本假定只跑一次。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- ① flow_step：能力目录类型化 ----------
ALTER TABLE `flow_step`
    ADD COLUMN `step_type` varchar(64) NOT NULL DEFAULT 'device.command' COMMENT '步骤类型 key' AFTER `name`,
    ADD COLUMN `type_config` json NULL COMMENT '类型专属配置 JSON' AFTER `step_type`,
    ADD COLUMN `default_params` json NULL COMMENT '通用默认输入 JSON' AFTER `type_config`,
    ADD KEY `idx_flow_step_type` (`step_type`);

-- 存量设备动作步骤：编码收进 type_config
UPDATE `flow_step`
SET `type_config` = JSON_OBJECT('deviceCode', `device_code`, 'actionCode', `action_code`)
WHERE `deleted` = b'0';

ALTER TABLE `flow_step`
    DROP COLUMN `device_code`,
    DROP COLUMN `action_code`;

-- ---------- ② flow_node：控制类节点与编排字段 ----------
ALTER TABLE `flow_node`
    MODIFY COLUMN `step_id` bigint NULL COMMENT '引用的能力（flow_step.id）。控制类节点为空',
    ADD COLUMN `name` varchar(128) NULL COMMENT '节点显示名。控制类节点必填；引用能力时为空' AFTER `node_id`,
    ADD COLUMN `step_type` varchar(64) NULL COMMENT '节点级类型覆盖。空则取 flow_step.step_type；控制类节点必填' AFTER `depends_on`,
    ADD COLUMN `type_config` json NULL COMMENT '节点级类型配置 JSON' AFTER `step_type`,
    ADD COLUMN `output_mapping` json NULL COMMENT '节点输出归一化到上下文的映射' AFTER `input_mapping`,
    ADD COLUMN `run_config` json NULL COMMENT '节点级运行覆盖：timeoutMs/maxAttempts/failureStrategy' AFTER `output_mapping`,
    ADD COLUMN `join_policy` varchar(32) NULL COMMENT '汇合策略：ALL_SUCCESS（默认）/ ANY_SUCCESS / ALL_DONE' AFTER `run_config`,
    ADD COLUMN `join_failure_policy` varchar(32) NULL COMMENT '前置失败策略：FAIL（默认）/ SKIP' AFTER `join_policy`,
    ADD COLUMN `branches` json NULL COMMENT '控制类节点的出口映射（route 专用）' AFTER `join_failure_policy`,
    ADD KEY `idx_flow_node_type` (`step_type`);

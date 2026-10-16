-- =============================================================================
-- Flow 能力输出声明增量迁移（MySQL 8.0+，存量库幂等，可重复执行）
-- 底座: boheng-frame (cn.boheng.frame)    目标库: boheng-frame
--
-- 背景:
--   能力目录（flow_step）新增「输出声明」（flow_step.output_schema）：
--     1. 声明这一步会往流程上下文里写哪些字段，JSON 结构 {输出字段: 说明}；
--     2. 发布期契约校验：声明非空时，节点 output_mapping 的源字段必须落在声明里
--        （另校验输出映射的上下文键不撞车），见 FlowGraphChecker#checkOutputContract；
--     3. 编排弹窗 NODE 来源的字段候选叠加声明的字段（output.前缀）。
--   未声明（空）= 输出动态，不参与白名单校验，行为与升级前完全一致。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. flow_step 加 output_schema 列（幂等：列存在则跳过）
-- -----------------------------------------------------------------------------
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_step'
      AND COLUMN_NAME = 'output_schema'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `flow_step` ADD COLUMN `output_schema` json NULL COMMENT ''输出声明 JSON：{输出字段: 说明}。能力写出的上下文字段契约'' AFTER `default_params`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

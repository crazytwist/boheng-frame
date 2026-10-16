-- =============================================================================
-- Flow 流程入参声明增量迁移（MySQL 8.0+，存量库幂等，可重复执行）
-- 底座: boheng-frame (cn.boheng.frame)    目标库: boheng-frame
--
-- 背景:
--   流程定义新增「入参声明」（flow_definition.input_params）：
--     1. 声明这条流程启动时要传的参数（键值对，值即默认值）；
--     2. 启动时未显式传参则以声明兜底（引擎端合并，声明打底、实际入参覆盖）；
--     3. 参数映射 INPUT 来源按它给出字段候选。
--   参数注释沿用 default_params 的 __comments__ 保留键约定。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. flow_definition 加 input_params 列（幂等：列存在则跳过）
-- -----------------------------------------------------------------------------
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'flow_definition'
      AND COLUMN_NAME = 'input_params'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `flow_definition` ADD COLUMN `input_params` json NULL COMMENT ''入参声明。启动入参默认值 + INPUT 映射字段候选，参数注释存 __comments__'' AFTER `remark`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- 实验流程 · flow_node 画布坐标（拖拽编排用）
-- 加 x / y 两个可空 INT 字段，记录节点在拖拽画布上的位置。
-- 空值由前端首次布局时回填；后端不参与布局计算，仅持久化坐标。
-- 幂等：MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，用 information_schema 判断 + 动态 DDL。
-- =============================================================================

SET NAMES utf8mb4;

-- x：画布横坐标
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'flow_node' AND COLUMN_NAME = 'x'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `flow_node` ADD COLUMN `x` int NULL COMMENT ''画布横坐标（拖拽编排）'' AFTER `branches`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- y：画布纵坐标
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'boheng-frame' AND TABLE_NAME = 'flow_node' AND COLUMN_NAME = 'y'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `flow_node` ADD COLUMN `y` int NULL COMMENT ''画布纵坐标（拖拽编排）'' AFTER `x`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

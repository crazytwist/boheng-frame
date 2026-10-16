-- =====================================================================
-- WMS 容器类型「最大数量」字段
-- 背景：枪头盒等耗材容器有固定装量（如 96 孔枪头盒 = 96 支枪头）。
--       批量上架时，若容器类型设置了 max_count，则实例的 current_count
--       （当前数量）默认取该最大值，省去手工填数。
-- 语义：max_count = 单个容器（或单个实例）能容纳的最大数量上限，为空表示不限定。
-- 可重复执行（幂等，通过 information_schema 判断列是否存在）。
-- =====================================================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_container_type'
      AND COLUMN_NAME = 'max_count'
);

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE wms_container_type ADD COLUMN max_count INT NULL COMMENT ''最大数量（如枪头盒 96；批量上架时实例当前数量默认取该值）'' AFTER life_cycles',
    'SELECT ''column max_count already exists, skip'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- WMS 物料预留列增量迁移（MySQL 8.0+，存量库幂等，可重复执行）
-- 对应修复计划 W-2.2：reservation_id / reserved_by_execution_id / reserved_until
-- =============================================================================

SET NAMES utf8mb4;

-- reservation_id
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND COLUMN_NAME = 'reservation_id'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD COLUMN `reservation_id` varchar(64) NULL DEFAULT NULL COMMENT ''预留号(同批共用，非预留为 NULL)'' AFTER `instance_status`',
    'SELECT ''column reservation_id already exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reserved_by_execution_id
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND COLUMN_NAME = 'reserved_by_execution_id'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD COLUMN `reserved_by_execution_id` varchar(64) NULL DEFAULT NULL COMMENT ''预留所有者(Flow 执行编号)'' AFTER `reservation_id`',
    'SELECT ''column reserved_by_execution_id already exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- reserved_until
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND COLUMN_NAME = 'reserved_until'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD COLUMN `reserved_until` datetime NULL DEFAULT NULL COMMENT ''预留到期时间'' AFTER `reserved_by_execution_id`',
    'SELECT ''column reserved_until already exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- indexes (幂等：索引名存在则跳过)
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND INDEX_NAME = 'idx_reserved_until_scan'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD INDEX `idx_reserved_until_scan`(`tenant_id`, `instance_status`, `reserved_until`, `id`)',
    'SELECT ''index idx_reserved_until_scan already exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND INDEX_NAME = 'idx_reserved_by_execution'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD INDEX `idx_reserved_by_execution`(`tenant_id`, `reserved_by_execution_id`, `instance_status`, `id`)',
    'SELECT ''index idx_reserved_by_execution already exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND INDEX_NAME = 'idx_reservation_id'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD INDEX `idx_reservation_id`(`tenant_id`, `reservation_id`, `id`)',
    'SELECT ''index idx_reservation_id already exists, skip'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

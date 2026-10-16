-- =====================================================================
-- WMS 槽位「适用物料」字段
-- 背景：槽位需要声明「适合放什么物料」，可绑定试剂(content_def)或容器(container_type)。
--       用于空间管理页的提示，以及批量上架时智能带出默认物料。
-- 存储：JSON 数组字符串，每项 { kind, materialId, materialCode, materialName }
--       kind = CONTENT（试剂/内容物）| CONTAINER（容器）
-- 示例：[{"kind":"CONTAINER","materialId":1,"materialCode":"TUBE_1.5","materialName":"1.5mL离心管"}]
-- 可重复执行（幂等，通过 information_schema 判断列是否存在）。
-- =====================================================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_slot_info'
      AND COLUMN_NAME = 'suitable_materials'
);

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE wms_slot_info ADD COLUMN suitable_materials VARCHAR(2048) NULL COMMENT ''适用物料（JSON 数组，可绑定试剂/容器）'' AFTER ext_data',
    'SELECT ''column suitable_materials already exists, skip'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

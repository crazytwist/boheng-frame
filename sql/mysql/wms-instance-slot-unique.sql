-- =====================================================================
-- WMS 物料实例 · 槽位唯一占用约束
-- 目的：同一个槽位同一时间只允许被一个未删除的实例占用（防并发重复上架）。
--
-- 实现思路（兼容逻辑删除）：
--   生成列 slot_unique_key = IF(deleted = 0, root_slot_id, NULL)
--     · 未删除且已落位  → 等于 root_slot_id，参与唯一约束
--     · 已删除 / 未落位 → NULL，NULL 不参与唯一约束（MySQL 特性）
--   唯一索引 uk_root_slot (tenant_id, slot_unique_key)
--
-- 本脚本幂等：已存在则跳过；存在冲突数据则给出提示且不做任何变更。
-- 可重复执行。
-- =====================================================================

DROP PROCEDURE IF EXISTS wms_add_slot_unique_index;

DELIMITER $$
CREATE PROCEDURE wms_add_slot_unique_index()
BEGIN
    DECLARE conflict_cnt INT DEFAULT 0;

    -- 1. 已存在则跳过（幂等）
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'wms_material_instance'
                 AND INDEX_NAME = 'uk_root_slot') THEN
        SELECT 'uk_root_slot 已存在，跳过' AS result;
    ELSE
        -- 2. 冲突检查：同一槽位被多个未删除实例占用
        SELECT COUNT(*) INTO conflict_cnt FROM (
            SELECT tenant_id, root_slot_id
            FROM wms_material_instance
            WHERE deleted = 0 AND root_slot_id IS NOT NULL
            GROUP BY tenant_id, root_slot_id
            HAVING COUNT(*) > 1
        ) t;

        IF conflict_cnt > 0 THEN
            SELECT CONCAT('存在 ', conflict_cnt, ' 个槽位被多个实例占用，请先清理冲突数据后重跑本脚本') AS result;
        ELSE
            -- 3. 生成列（未落位/已删除为 NULL）
            IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE()
                             AND TABLE_NAME = 'wms_material_instance'
                             AND COLUMN_NAME = 'slot_unique_key') THEN
                ALTER TABLE wms_material_instance
                    ADD COLUMN slot_unique_key bigint
                        GENERATED ALWAYS AS (IF(deleted = b'0', root_slot_id, NULL)) VIRTUAL
                        COMMENT '槽位唯一占位键(生成列)：未删除且已落位时为 root_slot_id，否则 NULL';
            END IF;
            -- 4. 唯一索引
            ALTER TABLE wms_material_instance
                ADD UNIQUE INDEX uk_root_slot (tenant_id, slot_unique_key);
            SELECT 'uk_root_slot 创建成功' AS result;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL wms_add_slot_unique_index();
DROP PROCEDURE wms_add_slot_unique_index;

-- =====================================================================
-- 冲突排查 SQL（需要时手动执行）
-- =====================================================================
-- SELECT i.id, i.instance_code, i.instance_name, i.root_slot_id, i.root_slot_code,
--        i.parent_instance_id, i.instance_status, i.create_time
-- FROM wms_material_instance i
-- JOIN (
--     SELECT tenant_id, root_slot_id
--     FROM wms_material_instance
--     WHERE deleted = 0 AND root_slot_id IS NOT NULL
--     GROUP BY tenant_id, root_slot_id HAVING COUNT(*) > 1
-- ) c ON c.root_slot_id = i.root_slot_id AND c.tenant_id = i.tenant_id
-- WHERE i.deleted = 0
-- ORDER BY i.root_slot_id, i.create_time;

-- =====================================================================
-- WMS 槽位占用状态修复脚本
-- 背景：早期创建/落位实例时未回写 wms_slot_info 的占用状态，
--       导致槽位始终停留在 FREE / occupied_qty=0。
-- 原则：以 wms_material_instance.root_slot_id 为唯一真相源反查重算，
--       不做增量加减（避免长期漂移）。
-- 可重复执行（幂等）。
-- =====================================================================

UPDATE wms_slot_info s
LEFT JOIN (
    SELECT tenant_id, root_slot_id, COUNT(*) AS cnt
    FROM wms_material_instance
    WHERE deleted = 0 AND root_slot_id IS NOT NULL
    GROUP BY tenant_id, root_slot_id
) i ON i.root_slot_id = s.id AND i.tenant_id = s.tenant_id
SET s.occupied_qty = IFNULL(i.cnt, 0),
    s.slot_status = CASE
        WHEN IFNULL(i.cnt, 0) > 0 THEN 'OCCUPIED'
        WHEN s.slot_status = 'OCCUPIED' THEN 'FREE'
        ELSE s.slot_status
    END,
    s.occupied_time = CASE
        WHEN IFNULL(i.cnt, 0) > 0 THEN IFNULL(s.occupied_time, NOW())
        ELSE NULL
    END
WHERE s.deleted = 0;

-- =====================================================================
-- 冲突排查：同一槽位被多个实例占用（业务上不允许，需人工决定去留）
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

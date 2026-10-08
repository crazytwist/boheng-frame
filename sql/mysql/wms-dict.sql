-- =============================================================================
-- Lab-WMS 字典初始化（MySQL 8.0+）
-- 底座: boheng-boot-mini (cn.boheng.frame)
--
-- 说明:
--   1. wms_material_type 是「物料类型」的唯一值域来源，被以下字段共同引用:
--        wms_material_info.material_type       物料主数据的类型
--        wms_zone_material_type.material_type  区域可存放的物料类型
--      二者必须使用同一套取值，切勿各自维护。
--   2. 前端 <dict-select> 选择、<dict-tag> 展示均依赖本字典。
--   3. 本脚本属于一次性初始化，重复执行会产生重复字典项。
--      执行前请确认 system_dict_type 中不存在 type = 'wms_material_type' 的记录。
--   4. 后续增删物料类型，直接走「系统管理 → 字典管理 → WMS 物料类型」维护，
--      无需改代码；但变更后需复核 wms_material_info / wms_zone_material_type 的存量数据。
-- =============================================================================

-- 1. 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`,
                                `updater`, `update_time`, `deleted`)
VALUES ('WMS 物料类型', 'wms_material_type', 0, 'WMS 物料类型（物料主数据 / 区域可存放类型共用）',
        'admin', NOW(), 'admin', NOW(), b'0');

-- 2. 字典数据
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`,
                                `css_class`, `remark`, `creator`, `create_time`,
                                `updater`, `update_time`, `deleted`)
VALUES (1, '试剂', 'REAGENT', 'wms_material_type', 0, 'primary', '', '液体/粉剂试剂，通常需要温控', 'admin', NOW(), 'admin', NOW(), b'0'),
       (2, '耗材', 'CONSUMABLE', 'wms_material_type', 0, 'success', '', '枪头 / 离心管等一次性耗材', 'admin', NOW(), 'admin', NOW(), b'0'),
       (3, '样本', 'SAMPLE', 'wms_material_type', 0, 'warning', '', '生物样本，通常要求一位一批', 'admin', NOW(), 'admin', NOW(), b'0'),
       (4, '标准品', 'STANDARD', 'wms_material_type', 0, 'info', '', '校准 / 质控用标准品', 'admin', NOW(), 'admin', NOW(), b'0'),
       (5, '工具', 'TOOL', 'wms_material_type', 0, 'info', '', '可复用器具', 'admin', NOW(), 'admin', NOW(), b'0');

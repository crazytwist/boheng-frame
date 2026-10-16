-- =============================================================================
-- 设备域字典初始化（MySQL 8.0+）
-- 底座: boheng-frame (cn.boheng.frame)
--
-- 说明:
--   1. device_type 是「设备类型」的唯一值域来源，被以下字段共同引用:
--        device_info.device_type_code    设备台账的类型
--        device_action.device_type_code  动作归属的设备类型
--      二者必须使用同一套取值，切勿各自维护。
--   2. 设备类型按「行为建模、不按型号」：一类设备一行，一台大设备（如液体工作站）
--      属于一个类型，该类型挂多个动作（能力集）。
--   3. 前端 <dict-select> 选择、<dict-tag> 展示均依赖本字典。
--   4. 本脚本属于一次性初始化，重复执行会产生重复字典项。
--      执行前请确认 system_dict_type 中不存在 type = 'device_type' 的记录。
--   5. 后续增删设备类型，直接走「系统管理 → 字典管理 → 设备类型」维护，无需改代码；
--      但变更后需复核 device_info / device_action 的存量数据。
-- =============================================================================

-- 1. 字典类型
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`,
                                `updater`, `update_time`, `deleted`)
VALUES ('设备类型', 'device_type', 0, '设备类型编码值域（device_info / device_action 共用）',
        'admin', NOW(), 'admin', NOW(), b'0');

-- 2. 字典数据
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`,
                                `css_class`, `remark`, `creator`, `create_time`,
                                `updater`, `update_time`, `deleted`)
VALUES -- 液体处理
       (1, '液体工作站', 'LIQUID_HANDLER', 'device_type', 0, 'primary', '', '分液/混匀/封板/离心，典型大设备（多动作）', 'admin', NOW(), 'admin', NOW(), b'0'),
       (2, '移液器', 'PIPETTOR', 'device_type', 0, 'primary', '', '精确移液/加样', 'admin', NOW(), 'admin', NOW(), b'0'),
       (3, '分液器', 'DISPENSER', 'device_type', 0, 'primary', '', '批量分液', 'admin', NOW(), 'admin', NOW(), b'0'),
       -- 样本处理
       (4, '离心机', 'CENTRIFUGE', 'device_type', 0, 'success', '', '离心分离', 'admin', NOW(), 'admin', NOW(), b'0'),
       (5, '洗板机', 'WASHER', 'device_type', 0, 'success', '', '微孔板清洗', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6, '摇床', 'SHAKER', 'device_type', 0, 'success', '', '振荡/孵育', 'admin', NOW(), 'admin', NOW(), b'0'),
       (7, '封板机', 'SEALER', 'device_type', 0, 'success', '', '微孔板封膜', 'admin', NOW(), 'admin', NOW(), b'0'),
       -- 检测分析
       (8, '读板器/酶标仪', 'PLATE_READER', 'device_type', 0, 'info', '', '读吸光度/荧光/发光', 'admin', NOW(), 'admin', NOW(), b'0'),
       (9, '分光光度计', 'SPECTROPHOTOMETER', 'device_type', 0, 'info', '', '光谱定量', 'admin', NOW(), 'admin', NOW(), b'0'),
       (10, 'pH 计', 'PH_METER', 'device_type', 0, 'info', '', 'pH 测定', 'admin', NOW(), 'admin', NOW(), b'0'),
       (11, '浊度仪', 'TURBIDIMETER', 'device_type', 0, 'info', '', '浊度测定', 'admin', NOW(), 'admin', NOW(), b'0'),
       (12, '自动滴定仪', 'TITRATOR', 'device_type', 0, 'info', '', '容量滴定', 'admin', NOW(), 'admin', NOW(), b'0'),
       (13, '重金属分析仪', 'ICP_MS', 'device_type', 0, 'info', '', '痕量元素检测（ICP-MS）', 'admin', NOW(), 'admin', NOW(), b'0'),
       (14, 'COD 消解仪', 'COD_ANALYZER', 'device_type', 0, 'info', '', '化学需氧量测定', 'admin', NOW(), 'admin', NOW(), b'0'),
       (15, '氨氮分析仪', 'AMMONIA_ANALYZER', 'device_type', 0, 'info', '', '氨氮测定', 'admin', NOW(), 'admin', NOW(), b'0'),
       -- 温控培养
       (16, '培养箱', 'INCUBATOR', 'device_type', 0, 'warning', '', '恒温培养', 'admin', NOW(), 'admin', NOW(), b'0'),
       (17, '热循环仪', 'THERMAL_CYCLER', 'device_type', 0, 'warning', '', 'PCR 扩增', 'admin', NOW(), 'admin', NOW(), b'0'),
       -- 搬运/机器人
       (18, '机械臂', 'ROBOT_ARM', 'device_type', 0, 'danger', '', '抓取/搬运', 'admin', NOW(), 'admin', NOW(), b'0'),
       (19, '移动机器人', 'AGV', 'device_type', 0, 'danger', '', '移动送样', 'admin', NOW(), 'admin', NOW(), b'0'),
       -- 预处理/称量
       (20, '样品预处理台', 'PREP_STATION', 'device_type', 0, 'info', '', '样品登记/预处理', 'admin', NOW(), 'admin', NOW(), b'0'),
       (21, '天平', 'BALANCE', 'device_type', 0, 'info', '', '称量', 'admin', NOW(), 'admin', NOW(), b'0');

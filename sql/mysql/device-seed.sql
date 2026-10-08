-- ----------------------------------------------------------------------------
-- 设备域 种子数据（一期：读板机 PLATE_READER）
--
-- 目的：
--   1. 让设备台账 / 动作 / 属性 / 参数集 四个页面开箱有数据，便于验收；
--   2. 为下一步 **SimulatedDriver** 提供配置前置（仿真按 param_schema 校验后返回假数据）。
--
-- 口径：
--   * tenant_id = 1（本库主租户，与 system_users 一致）
--   * 固定小 id（9xxxxx 段）便于反复重跑，避免雪花 id 漂移
--   * 幂等：先按 id 清理再插入；生成列（*_code_key）由 DB 自动维护，不写入
--   * 两台设备都开 simulation_mode=1 —— Tecan 接口文档到位后关掉即可切真机
--
-- ⚠️ 这是一期示例配置，真实设备的动作/参数应以厂商文档为准，不可当作接口契约。
-- ----------------------------------------------------------------------------

SET NAMES utf8mb4;

-- 1. 设备台账 ----------------------------------------------------------------
DELETE FROM `device_info` WHERE `id` IN (900001, 900002);
INSERT INTO `device_info`
(`id`, `device_code`, `device_name`, `image_url`, `device_type_code`, `model`, `vendor`, `serial_no`, `driver_type`, `status`,
 `connection_type`, `endpoint_url`, `mqtt_topic_prefix`, `callback_enabled`, `poll_interval_sec`,
 `capability_source`, `simulation_mode`, `discovery_type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(900001, 'DEV-001', '读板机 1 号', NULL, 'PLATE_READER', 'Infinite 200 PRO', 'Tecan', 'SN-SIM-001', 'SIMULATED', 'OFFLINE',
 'SIMULATED', NULL, NULL, b'0', 5,
 'MANUAL', b'1', 'MANUAL', '仿真设备（Tecan 接口文档到位后切换驱动）', 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(900002, 'DEV-002', '读板机 2 号', NULL, 'PLATE_READER', 'Infinite 200 PRO', 'Tecan', 'SN-SIM-002', 'TECAN_READER_NETWORK', 'OFFLINE',
 'HTTP', 'http://192.168.1.100:8080', NULL, b'0', 5,
 'MANUAL', b'1', 'MANUAL', '待接入真机；当前仍走仿真，endpoint 为占位', 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1);

-- 2. 设备动作（读板机）--------------------------------------------------------
DELETE FROM `device_action` WHERE `id` BETWEEN 910001 AND 910005;
INSERT INTO `device_action`
(`id`, `device_type_code`, `action_code`, `action_name`, `standard_feature`, `param_schema`, `vendor_ref`,
 `source`, `estimate_duration_ms`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(910001, 'PLATE_READER', 'READ_PLATE', '读板', 'com.sila_standard.run_control',
 '{"wavelength":{"label":"主波长","type":"INTEGER","unit":"nm","required":true,"default":450,"constraints":{"min":200,"max":1000}},"mode":{"label":"检测模式","type":"ENUM","required":true,"default":"ABSORBANCE","constraints":["ABSORBANCE","FLUORESCENCE","LUMINESCENCE"]},"plateType":{"label":"板型","type":"STRING","required":true,"default":"96-well","source":"DEVICE_DECLARED"},"shakeBeforeRead":{"label":"读前振荡","type":"BOOLEAN","default":false},"shakeSeconds":{"label":"振荡时长","type":"INTEGER","unit":"s","default":5}}',
 'Magellan.ReadPlate', 'MANUAL', 30000, 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(910002, 'PLATE_READER', 'OPEN_DOOR', '开仓门', NULL, NULL,
 NULL, 'MANUAL', 2000, 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(910003, 'PLATE_READER', 'CLOSE_DOOR', '关仓门', NULL, NULL,
 NULL, 'MANUAL', 2000, 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(910004, 'PLATE_READER', 'STOP', '急停', NULL, NULL,
 NULL, 'MANUAL', 1000, 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(910005, 'PLATE_READER', 'GET_STATUS', '查询状态', NULL, NULL,
 NULL, 'MANUAL', 500, 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1);

-- 3. 设备属性（读板机，遥测）--------------------------------------------------
DELETE FROM `device_property` WHERE `id` BETWEEN 920001 AND 920004;
INSERT INTO `device_property`
(`id`, `device_type_code`, `property_code`, `property_name`, `data_type`, `unit`, `readable`, `subscribable`,
 `poll_interval_sec`, `source`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(920001, 'PLATE_READER', 'TEMPERATURE', '腔体温度', 'DECIMAL', '℃', b'1', b'0', 30, 'MANUAL', 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(920002, 'PLATE_READER', 'DOOR_STATE', '仓门状态', 'ENUM', NULL, b'1', b'0', 10, 'MANUAL', 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(920003, 'PLATE_READER', 'LAMP_HOURS', '光源使用时长', 'INTEGER', 'h', b'1', b'0', 300, 'MANUAL', 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(920004, 'PLATE_READER', 'RUN_STATUS', '运行状态', 'STRING', NULL, b'1', b'1', 5, 'MANUAL', 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1);

-- 4. 参数集（读板机 + READ_PLATE）--------------------------------------------
DELETE FROM `device_param_set` WHERE `id` BETWEEN 930001 AND 930002;
INSERT INTO `device_param_set`
(`id`, `param_set_code`, `param_set_name`, `device_type_code`, `action_code`, `params_json`, `validated`, `validated_by`,
 `validated_time`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(930001, 'PS-ABSORB-450', '吸光度 450nm 标准读板', 'PLATE_READER', 'READ_PLATE',
 '{"wavelength":450,"mode":"ABSORBANCE","plateType":"96-well","shakeBeforeRead":true,"shakeSeconds":5}',
 b'1', 'admin', '2026-09-28 11:45:00', 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1),
(930002, 'PS-FLUOR-485', '荧光 485/535 检测（待验证）', 'PLATE_READER', 'READ_PLATE',
 '{"wavelength":485,"mode":"FLUORESCENCE","plateType":"96-well","shakeBeforeRead":false}',
 b'0', NULL, NULL, 0, 'admin', '2026-09-28 11:45:00', '', '2026-09-28 11:45:00', b'0', 1);

-- =====================================================================
-- 设备域：三个「区级设备」+ 拉取任务能力
-- 背景：三个区（RA-LiquidWorkstation / RA-TBottle / RA-Incubation）作为
--       区级虚拟设备登记进 device_info，动作统一为「拉取任务」（PULL_TASK）。
--       任务按区隔离：节点 deviceCode 锁定区编码，区级程序 claim 时只能拿本区任务。
-- 语义：区 = 设备台账里的一条记录；区内多台真实设备由区级程序自己调度。
-- 幂等：字典 / 动作 / 设备均按存在性判断后插入，可重复执行。
-- 主键：雪花 id（应用侧生成，非自增），此处显式给定，避免 DB 报 id 无默认值。
-- =====================================================================

-- 1. 设备类型字典：拉取工作站（三个区共用一个类型）
INSERT INTO system_dict_data (sort, label, value, dict_type, status, remark)
SELECT t.sort, '拉取工作站', 'PULL_STATION', 'device_type', 0, '区级虚拟设备，动作统一为拉取任务'
FROM (SELECT COALESCE(MAX(sort), 0) + 1 AS sort FROM system_dict_data WHERE dict_type = 'device_type') t
WHERE NOT EXISTS (
    SELECT 1 FROM system_dict_data
    WHERE dict_type = 'device_type' AND value = 'PULL_STATION' AND deleted = b'0'
);

-- 2. 拉取任务动作：PULL_STATION -> PULL_TASK（device.task 认领时反查能力集用）
INSERT INTO device_action (id, device_type_code, action_code, action_name, source, result_mode, status)
SELECT 7515389695010802412, 'PULL_STATION', 'PULL_TASK', '拉取任务', 'MANUAL', 'CALLBACK', 0
WHERE NOT EXISTS (
    SELECT 1 FROM device_action
    WHERE device_type_code = 'PULL_STATION' AND action_code = 'PULL_TASK' AND deleted = b'0'
);

-- 3. 三个区级设备（driver_type=SIMULATED：区级虚拟设备不直接连仪器）
INSERT INTO device_info (id, device_code, device_name, device_type_code, driver_type, status, connection_type, tenant_id, remark)
SELECT 7515389695010800475, 'RA-LiquidWorkstation', '液体工作站区', 'PULL_STATION', 'SIMULATED', 'ONLINE', 'SIMULATED', 0, '区级虚拟设备'
WHERE NOT EXISTS (SELECT 1 FROM device_info WHERE device_code = 'RA-LiquidWorkstation' AND deleted = b'0');

INSERT INTO device_info (id, device_code, device_name, device_type_code, driver_type, status, connection_type, tenant_id, remark)
SELECT 7515389695010800841, 'RA-TBottle', 'T瓶区', 'PULL_STATION', 'SIMULATED', 'ONLINE', 'SIMULATED', 0, '区级虚拟设备'
WHERE NOT EXISTS (SELECT 1 FROM device_info WHERE device_code = 'RA-TBottle' AND deleted = b'0');

INSERT INTO device_info (id, device_code, device_name, device_type_code, driver_type, status, connection_type, tenant_id, remark)
SELECT 7515389695010800347, 'RA-Incubation', '孵育区', 'PULL_STATION', 'SIMULATED', 'ONLINE', 'SIMULATED', 0, '区级虚拟设备'
WHERE NOT EXISTS (SELECT 1 FROM device_info WHERE device_code = 'RA-Incubation' AND deleted = b'0');

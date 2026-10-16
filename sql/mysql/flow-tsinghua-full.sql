-- =============================================================================
-- 清华流程 v1（flow_tsinghua）：按业务方流程图补齐 Jurkat 支线 + 293T 病毒收获尾段
-- 幂等：全部按 step_key / (definition_id, node_id) 判存，可重复执行
-- 雪花 id 由脚本用 Python 生成（device_info 主键非自增，SQL 直插必须显式给 id）
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 一、能力目录补 7 条（对应流程图里现有目录没有的动作）
-- 图上每个节点的标题栏就是它该去的区 → deviceCode 按区映射：
--   培养区 → RA-Incubation / T瓶处理区 → RA-TBottle / 移液区 → RA-LiquidWorkstation
-- -----------------------------------------------------------------------------

-- 293T 线尾段：病毒收获后处理（T瓶处理区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000001, 'step_293_post_virus_harvest', '293T病毒收获后处理', 'device.task',
       '{"deviceCode":"RA-TBottle","actionCode":"PULL_TASK"}', '{}', 3600000, 1,
       '清华流程 293T 线：病毒收获后处理（T瓶处理区）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_293_post_virus_harvest');

-- Jurkat 线：病毒感染（移液区）——这是两条支线的汇合点
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000002, 'step_jurkat_virus_infection', 'Jurkat-293T病毒感染', 'device.task',
       '{"deviceCode":"RA-LiquidWorkstation","actionCode":"PULL_TASK"}', '{}', 3600000, 1,
       '清华流程 Jurkat 线：293T 病毒感染 Jurkat（移液区）。两条支线在此汇合', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_jurkat_virus_infection');

-- Jurkat 线：细胞收获后处理（T瓶处理区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000003, 'step_jurkat_post_cell_harvest', 'Jurkat细胞收获后处理', 'device.task',
       '{"deviceCode":"RA-TBottle","actionCode":"PULL_TASK"}', '{}', 3600000, 1,
       '清华流程 Jurkat 线：细胞收获后处理（T瓶处理区）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_jurkat_post_cell_harvest');

-- Jurkat 线：培养基预热（T瓶处理区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000004, 'step_medium_preheat', '培养基预热', 'device.task',
       '{"deviceCode":"RA-TBottle","actionCode":"PULL_TASK"}', '{}', 1800000, 1,
       '清华流程 Jurkat 线：培养基预热（T瓶处理区）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_medium_preheat');

-- Jurkat 线：细胞过滤（移液区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000005, 'step_jurkat_cell_filter', 'Jurkat细胞过滤', 'device.task',
       '{"deviceCode":"RA-LiquidWorkstation","actionCode":"PULL_TASK"}', '{}', 1800000, 1,
       '清华流程 Jurkat 线：细胞过滤（移液区）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_jurkat_cell_filter');

-- Jurkat 线：类器官培养（**T瓶处理区**，按流程图节点标题栏，不是培养区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000006, 'step_jurkat_organoid_culture', 'Jurkat类器官培养', 'device.task',
       '{"deviceCode":"RA-TBottle","actionCode":"PULL_TASK"}', '{}', 3600000, 1,
       '清华流程 Jurkat 线：类器官培养（T瓶处理区。按流程图节点标题栏归属）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_jurkat_organoid_culture');

-- Jurkat 线：类器官收获前处理（T瓶处理区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000007, 'step_jurkat_organoid_pre_harvest', 'Jurkat类器官收获前处理', 'device.task',
       '{"deviceCode":"RA-TBottle","actionCode":"PULL_TASK"}', '{}', 3600000, 1,
       '清华流程 Jurkat 线：类器官收获前处理（T瓶处理区）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_jurkat_organoid_pre_harvest');

-- Jurkat 线：类器官收获后处理（移液区）
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, remark, status, creator, updater, create_time, update_time, deleted, tenant_id)
SELECT 9000000000000000008, 'step_jurkat_organoid_post_hrv', 'Jurkat类器官收获后处理', 'device.task',
       '{"deviceCode":"RA-LiquidWorkstation","actionCode":"PULL_TASK"}', '{}', 3600000, 1,
       '清华流程 Jurkat 线：类器官收获后处理（移液区）', 0, 'system', 'system', NOW(), NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'step_jurkat_organoid_post_hrv');

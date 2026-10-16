-- =============================================================================
-- 清华流程 v1（flow_tsinghua）节点编排：按业务方流程图补齐
-- 结构：293T 线（16 节点）+ Jurkat 线（19 节点），两线在「Jurkat-293T病毒感染」汇合，之后单线到结束
-- 现有 13 节点保留（sort_no 0-12），本脚本追加 sort_no 13 起，并把依赖关系重排为流程图拓扑
-- 幂等：按 (definition_id, node_id) 判存；依赖用 UPDATE 幂等覆盖
-- =============================================================================

SET NAMES utf8mb4;

-- 定义 ID = flow_tsinghua v1（按 pipeline_key 取，不写死雪花值）
SET @def := (SELECT id FROM flow_definition WHERE pipeline_key = 'flow_tsinghua' AND version = 1 LIMIT 1);

-- =============================================================================
-- 一、按流程图重排 293T 线依赖
-- 图上 293T 线顺序：
--   培养1 → 传代前处理 → 细胞计数 → 传代后处理 → 培养2 → 二次传代前处理 → 细胞计数
--   → 二次传代后处理 → 293T细胞前处理 → 293T细胞转染 → 病毒浓缩 → 培养3
--   → 病毒收获前处理 → 培养4 → 病毒收获后处理 → 病毒浓缩
-- =============================================================================

-- 起点
UPDATE flow_node SET depends_on = '[]' WHERE definition_id = @def AND node_id = 'incubation';

UPDATE flow_node SET depends_on = '["incubation"]'
WHERE definition_id = @def AND node_id = 'step_293_pre_cell_passage';

UPDATE flow_node SET depends_on = '["step_293_pre_cell_passage"]'
WHERE definition_id = @def AND node_id = 'step_cell_count';

UPDATE flow_node SET depends_on = '["step_cell_count"]'
WHERE definition_id = @def AND node_id = 'step_293_post_cell_passage';

UPDATE flow_node SET depends_on = '["step_293_post_cell_passage"]'
WHERE definition_id = @def AND node_id = 'incubation_2';

UPDATE flow_node SET depends_on = '["incubation_2"]'
WHERE definition_id = @def AND node_id = 'step_293_pre_cell_passage_2';

UPDATE flow_node SET depends_on = '["step_293_pre_cell_passage_2"]'
WHERE definition_id = @def AND node_id = 'step_cell_count_2';

UPDATE flow_node SET depends_on = '["step_cell_count_2"]'
WHERE definition_id = @def AND node_id = 'step_293_post_cell_passage_2';

UPDATE flow_node SET depends_on = '["step_293_post_cell_passage_2"]'
WHERE definition_id = @def AND node_id = 'step_293_pre_cell_transfection';

UPDATE flow_node SET depends_on = '["step_293_pre_cell_transfection"]'
WHERE definition_id = @def AND node_id = 'step_293_cell_transfection';

-- 「病毒浓缩」出现两次：第一次在转染之后、培养3之前
INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000013, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_pre_virus_concentration' LIMIT 1),
       'step_pre_virus_concentration_2', NULL, 13, '["step_293_cell_transfection"]',
       NULL, NULL, NULL, NULL, 1400, 100, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'step_pre_virus_concentration_2'
);

UPDATE flow_node SET depends_on = '["step_pre_virus_concentration_2"]'
WHERE definition_id = @def AND node_id = 'incubation_3';

UPDATE flow_node SET depends_on = '["incubation_3"]'
WHERE definition_id = @def AND node_id = 'step_pre_virus_concentration';

UPDATE flow_node SET depends_on = '["step_pre_virus_concentration"]'
WHERE definition_id = @def AND node_id = 'incubation_4';

-- 尾段：病毒收获后处理 → 病毒浓缩（终点，汇入 Jurkat 线的病毒感染）
INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000014, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_293_post_virus_harvest' LIMIT 1),
       'step_293_post_virus_harvest', NULL, 14, '["incubation_4"]',
       NULL, NULL, NULL, NULL, 1550, 100, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'step_293_post_virus_harvest'
);

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000015, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_pre_virus_concentration' LIMIT 1),
       'step_pre_virus_concentration_3', NULL, 15, '["step_293_post_virus_harvest"]',
       NULL, NULL, NULL, NULL, 1700, 100, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'step_pre_virus_concentration_3'
);

-- =============================================================================
-- 二、Jurkat 线（sort_no 16 起）
-- 图上顺序：培养1 → 传代前处理 → 细胞计数 → 传代后处理 → 培养2 → 二次传代前处理
--   → 细胞计数 → 病毒收获前处理 → 细胞计数 →【病毒感染 = 与 293T 线汇合点】
--   → 细胞收获后处理 → 培养3 → 细胞转染 → 培养基预热 → 细胞过滤
--   → 类器官培养 → 培养4 → 类器官收获前处理 → 类器官收获后处理（结束）
-- 复用 293T 线已有的培养/传代/计数能力（同能力可在同一版本挂多次，node_id 自动加后缀）
-- =============================================================================

-- Jurkat 细胞培养1 —— 与 293T 培养1 并行起跑（依赖为空 = 另一个起点）
INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000016, @def,
       (SELECT id FROM flow_step WHERE step_key = 'incubation' LIMIT 1),
       'jurkat_incubation', NULL, 16, '[]',
       NULL, NULL, NULL, NULL, 150, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_incubation');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000017, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_293_pre_cell_passage' LIMIT 1),
       'jurkat_pre_cell_passage', NULL, 17, '["jurkat_incubation"]',
       NULL, NULL, NULL, NULL, 300, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_pre_cell_passage');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000018, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_cell_count' LIMIT 1),
       'jurkat_cell_count', NULL, 18, '["jurkat_pre_cell_passage"]',
       NULL, NULL, NULL, NULL, 450, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_cell_count');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000019, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_293_post_cell_passage' LIMIT 1),
       'jurkat_post_cell_passage', NULL, 19, '["jurkat_cell_count"]',
       NULL, NULL, NULL, NULL, 600, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_post_cell_passage');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000020, @def,
       (SELECT id FROM flow_step WHERE step_key = 'incubation' LIMIT 1),
       'jurkat_incubation_2', NULL, 20, '["jurkat_post_cell_passage"]',
       NULL, NULL, NULL, NULL, 750, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_incubation_2');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000021, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_293_pre_cell_passage' LIMIT 1),
       'jurkat_pre_cell_passage_2', NULL, 21, '["jurkat_incubation_2"]',
       NULL, NULL, NULL, NULL, 900, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_pre_cell_passage_2');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000022, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_cell_count' LIMIT 1),
       'jurkat_cell_count_2', NULL, 22, '["jurkat_pre_cell_passage_2"]',
       NULL, NULL, NULL, NULL, 1050, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_cell_count_2');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000023, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_pre_virus_concentration' LIMIT 1),
       'jurkat_pre_virus_harvest', NULL, 23, '["jurkat_cell_count_2"]',
       NULL, NULL, NULL, NULL, 1200, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_pre_virus_harvest');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000024, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_cell_count' LIMIT 1),
       'jurkat_cell_count_3', NULL, 24, '["jurkat_pre_virus_harvest"]',
       NULL, NULL, NULL, NULL, 1350, 260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_cell_count_3');

-- ★ 汇合点：293T 线末节点 + Jurkat 线细胞计数3 同时作为前置
INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000025, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_jurkat_virus_infection' LIMIT 1),
       'jurkat_virus_infection', NULL, 25,
       '["step_pre_virus_concentration_3","jurkat_cell_count_3"]',
       NULL, NULL, 'ALL_SUCCESS', NULL, 1500, 300, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_virus_infection');

-- =============================================================================
-- 三、Jurkat 汇合后单线（sort_no 26 起）——流程图里垂直向下的一段
-- =============================================================================

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000026, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_jurkat_post_cell_harvest' LIMIT 1),
       'jurkat_post_cell_harvest', NULL, 26, '["jurkat_virus_infection"]',
       NULL, NULL, NULL, NULL, 1500, 420, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_post_cell_harvest');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000027, @def,
       (SELECT id FROM flow_step WHERE step_key = 'incubation' LIMIT 1),
       'jurkat_incubation_3', NULL, 27, '["jurkat_post_cell_harvest"]',
       NULL, NULL, NULL, NULL, 1500, 540, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_incubation_3');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000028, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_293_cell_transfection' LIMIT 1),
       'jurkat_cell_transfection', NULL, 28, '["jurkat_incubation_3"]',
       NULL, NULL, NULL, NULL, 1500, 660, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_cell_transfection');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000029, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_medium_preheat' LIMIT 1),
       'jurkat_medium_preheat', NULL, 29, '["jurkat_cell_transfection"]',
       NULL, NULL, NULL, NULL, 1500, 780, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_medium_preheat');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000030, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_jurkat_cell_filter' LIMIT 1),
       'jurkat_cell_filter', NULL, 30, '["jurkat_medium_preheat"]',
       NULL, NULL, NULL, NULL, 1500, 900, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_cell_filter');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000031, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_jurkat_organoid_culture' LIMIT 1),
       'jurkat_organoid_culture', NULL, 31, '["jurkat_cell_filter"]',
       NULL, NULL, NULL, NULL, 1500, 1020, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_organoid_culture');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000032, @def,
       (SELECT id FROM flow_step WHERE step_key = 'incubation' LIMIT 1),
       'jurkat_incubation_4', NULL, 32, '["jurkat_organoid_culture"]',
       NULL, NULL, NULL, NULL, 1500, 1140, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_incubation_4');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000033, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_jurkat_organoid_pre_harvest' LIMIT 1),
       'jurkat_organoid_pre_harvest', NULL, 33, '["jurkat_incubation_4"]',
       NULL, NULL, NULL, NULL, 1500, 1260, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_organoid_pre_harvest');

-- 终点：类器官收获后处理（图中「结束」之前的最后一个节点）
INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type, type_config, join_policy, join_failure_policy, x, y, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 9001000000000000034, @def,
       (SELECT id FROM flow_step WHERE step_key = 'step_jurkat_organoid_post_hrv' LIMIT 1),
       'jurkat_organoid_post_hrv', NULL, 34, '["jurkat_organoid_pre_harvest"]',
       NULL, NULL, NULL, NULL, 1500, 1380, 'system', NOW(), 'system', NOW(), b'0', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM flow_node WHERE definition_id = @def AND node_id = 'jurkat_organoid_post_hrv');

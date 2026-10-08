-- =============================================================================
-- 清理本轮流水功能验证产生的测试数据
-- 生成时间: 2026-09-26
-- 目标库: boheng-frame
-- =============================================================================
--
-- 背景：
--   为验证「上架 / 下架 / 转移 / 消耗」四个接口 + 物料流水，在
--   Zone_Rack_Reagent_R1C2 上创建了测试夹具 INST-580689（96 孔板，含 A1~D6 共 24 孔），
--   并产生了 9 条流水。这些数据全部是本会话产生的，不是历史业务数据。
--
-- 受影响对象清单（执行前可先跑下方「盘点」段核对）:
--   wms_material_instance        25 行 = INST-580689（根） + 24 个子实例
--   wms_material_movement         9 行 = 6 条 CONSUME + MOVE + TAKE_OUT + PUT_IN
--   占用槽位:  Zone_Rack_Reagent_R1C2
--
-- 不受影响:
--   instance-0960-001  （在 Zone_Rack_Reagent_R1C1，非本会话测试数据，保留）
--
-- 用法:
--   docker exec -i mysql mysql -uroot -proot1234 -h127.0.0.1 --default-character-set=utf8mb4 \
--     "boheng-frame" < sql/mysql/wms-cleanup-test-data.sql
--
-- ⚠️ 本脚本会 DELETE 数据。执行前请确认上表清单，并已备份。
-- =============================================================================

USE `boheng-frame`;

-- -----------------------------------------------------------------------------
-- 【第 0 段】盘点（只读，先跑这段确认清单再往下执行）
-- -----------------------------------------------------------------------------
SELECT '== 待清理的实例 ==' AS stage;
SELECT instance_code, instance_status, current_count, root_slot_code, create_time
FROM wms_material_instance
WHERE instance_code LIKE 'INST-580689%'
ORDER BY instance_code;

SELECT '== 待清理的流水 ==' AS stage;
SELECT id, movement_type, instance_code, operation_id, idempotent_key, operate_time
FROM wms_material_movement
ORDER BY id;

-- -----------------------------------------------------------------------------
-- 【第 1 段】删除测试流水
--   流水表设计上「只增不改」，这里因为是**建表后的验证数据**、且未对外提供，
--   直接物理清除；正式环境的错账请走 ADJUST 冲正，不要删。
-- -----------------------------------------------------------------------------
DELETE FROM wms_material_movement
WHERE instance_code LIKE 'INST-580689%';
-- 若只想删消耗那 6 条、保留 MOVE/TAKE_OUT/PUT_IN 的上下架轨迹，改用：
-- DELETE FROM wms_material_movement
-- WHERE instance_code LIKE 'INST-580689-%' AND movement_type = 'CONSUME';

-- -----------------------------------------------------------------------------
-- 【第 2 段】删除测试实例（先删子实例，再删根实例）
--   注意：物理 DELETE，不走逻辑删除——因为这是纯测试夹具，
--   逻辑删会留下 deleted=1 的行，反而干扰后续唯一索引判断。
-- -----------------------------------------------------------------------------
DELETE FROM wms_material_instance
WHERE instance_code LIKE 'INST-580689-%';      -- 24 个子实例

DELETE FROM wms_material_instance
WHERE instance_code = 'INST-580689' ;          -- 根实例

-- -----------------------------------------------------------------------------
-- 【第 3 段】复位槽位占用状态
--   槽位占用真相源是 wms_material_instance.root_slot_id，实例删掉了，
--   槽位必须同步回到 FREE，否则会永久锁死（gen 列门控只认 deleted，不认悬空引用）
-- -----------------------------------------------------------------------------
UPDATE wms_slot_info s
SET s.slot_status  = 'FREE',
    s.occupied_qty = 0,
    s.occupied_time = NULL
WHERE s.slot_code = 'Zone_Rack_Reagent_R1C2';

-- -----------------------------------------------------------------------------
-- 【第 4 段】清理后复查（期望：0 行实例 / 0 行流水 / 槽位 FREE）
-- -----------------------------------------------------------------------------
SELECT '== 复查：残留实例（应为空） ==' AS stage;
SELECT COUNT(*) AS remain_instances FROM wms_material_instance
WHERE instance_code LIKE 'INST-580689%';

SELECT '== 复查：残留流水（应为空） ==' AS stage;
SELECT COUNT(*) AS remain_movements FROM wms_material_movement;

SELECT '== 复查：R1C2 槽位状态 ==' AS stage;
SELECT slot_code, slot_status, occupied_qty FROM wms_slot_info
WHERE slot_code = 'Zone_Rack_Reagent_R1C2';

SELECT '== 复查：其他实例是否受影响（应只剩 instance-0960-001 落位 R1C1） ==' AS stage;
SELECT instance_code, instance_status, root_slot_code FROM wms_material_instance
WHERE root_slot_code IS NOT NULL;

-- =============================================================================
-- 【备选：不想全清，只把 A1~A6 恢复原状 + 清掉 6 条消耗流水】
-- 若希望保留 INST-580689 作为「已上架物料的演示样例」，改用下面几段，跳过第 1/2 段：
-- -----------------------------------------------------------------------------
-- -- 1) A1~A6 状态回滚（原状 = AVAILABLE / current_count 为空 / 体积不动）
-- UPDATE wms_material_instance
-- SET instance_status = 'AVAILABLE', current_count = NULL
-- WHERE instance_code IN
--   ('INST-580689-A1','INST-580689-A2','INST-580689-A3',
--    'INST-580689-A4','INST-580689-A5','INST-580689-A6');
--
-- -- 2) 根实例状态回滚（下架测试把它改成了 IN_USE）
-- UPDATE wms_material_instance
-- SET instance_status = 'AVAILABLE'
-- WHERE instance_code = 'INST-580689';
--
-- -- 3) 删除 6 条消耗测试流水（保留 MOVE/TAKE_OUT/PUT_IN 的上下架轨迹做样例）
-- DELETE FROM wms_material_movement
-- WHERE instance_code LIKE 'INST-580689-%' AND movement_type = 'CONSUME';
-- =============================================================================

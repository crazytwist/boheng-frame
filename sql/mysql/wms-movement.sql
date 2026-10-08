-- =============================================================================
-- Lab-WMS · 物料流水（上架 / 下架 / 转移 / 消耗 / 状态变更）
-- 版本: v1.5-movement    底座: boheng-frame (cn.boheng.frame)    目标库: MySQL 8.0+
-- 依赖: wms-instance-minimal.sql (5 表) + wms-instance-slot-unique.sql (uk_root_slot)
-- =============================================================================
--
-- 设计前提（非常重要）:
--   当前模型是「位置即实例」——槽位占用状态不是独立记账，而是由
--   wms_material_instance.root_slot_id 反查推导（见 refreshSlotOccupancy）。
--   因此上架/下架/转移本质是【同一个字段的三次变更】:
--       上架 PUT_IN    root_slot_id : NULL -> S     入位（进入在架状态）
--       下架 TAKE_OUT  root_slot_id : S    -> NULL  出位（离开在架状态）
--       转移 MOVE      root_slot_id : S1   -> S2    换位（仍在架）
--   所以流水的主体是【实例】，而不是「槽位 × 批次 × 数量」账。
--
-- 记录粒度: 单条事件（一次操作 = 一条流水，MOVE 同时带 from/to）
--   对比「双条借贷（OUT + IN）」:
--     1) 一个业务事实对应一条记录，不会出现「下了没上」的中间态；
--     2) 整树转移（1 载体 + 96 孔位）只记顶层 1 条，不会刷出 194 条；
--     3) 槽位维度进出统计用 from_slot_id / to_slot_id 双索引即可。
--   批量操作的 N 条明细通过 operation_id 归组（吸收「操作头+明细」的优点但不加表）。
--
-- 只增不改: 流水不提供 update / delete 接口。操作错了写一条 ADJUST 反向冲正流水，
--   不删原记录（审计表标准做法）。本表虽保留 deleted 列（与其余表保持框架一致），
--   但业务侧永不置 1。
-- =============================================================================

DROP TABLE IF EXISTS `wms_material_movement`;
CREATE TABLE `wms_material_movement` (
  `id` bigint NOT NULL COMMENT '主键',
  `movement_type` varchar(32) NOT NULL COMMENT '流水类型: CREATE 建账/PUT_IN 上架/TAKE_OUT 下架/MOVE 转移/CONSUME 消耗/STATUS_CHANGE 状态变更/RESERVE 预留/RELEASE 释放预留/ADJUST 冲正',
  `biz_source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '业务来源: MANUAL 手工/TASK 调度任务/DEVICE 设备/IMPORT 导入',
  `operation_id` varchar(64) NULL DEFAULT NULL COMMENT '操作批次号(一次批量操作的多条明细共享，用于归组还原「一次操作」)',

  `instance_id` bigint NOT NULL COMMENT '实例编号(流水主体，可以是根实例也可以是子实例)',
  `instance_code` varchar(64) NOT NULL COMMENT '实例编码(冗余，编码寻址)',
  `root_instance_id` bigint NULL DEFAULT NULL COMMENT '根实例编号(冗余，定位本条流水属于哪个顶层容器)',
  `container_type_code` varchar(64) NULL DEFAULT NULL COMMENT '容器类型编码快照',
  `content_def_code` varchar(64) NULL DEFAULT NULL COMMENT '内容物编码快照(便于按物质筛流水，避免 join 实例表)',
  `content_type` varchar(32) NULL DEFAULT NULL COMMENT '内容物类型快照',

  `from_slot_id` bigint NULL DEFAULT NULL COMMENT '源槽位编号(NULL=上架/建账，操作前不在架上)',
  `from_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '源槽位编码(冗余)',
  `from_zone_code` varchar(64) NULL DEFAULT NULL COMMENT '源区域编码(冗余，便于按区域筛流水)',
  `to_slot_id` bigint NULL DEFAULT NULL COMMENT '目标槽位编号(NULL=下架/消耗，操作后不在架上)',
  `to_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '目标槽位编码(冗余)',
  `to_zone_code` varchar(64) NULL DEFAULT NULL COMMENT '目标区域编码(冗余)',

  `before_status` varchar(32) NULL DEFAULT NULL COMMENT '变更前实例状态',
  `after_status` varchar(32) NULL DEFAULT NULL COMMENT '变更后实例状态',

  `before_qty` int NULL DEFAULT NULL COMMENT '变更前数量(个)，离散计数类(如离心管根数)填写',
  `change_qty` int NULL DEFAULT NULL COMMENT '变更量(个，正为增负为减)',
  `after_qty` int NULL DEFAULT NULL COMMENT '变更后数量(个)',
  `before_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '变更前体积(μL)，液体类填写',
  `change_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '变更量(μL，负为消耗)',
  `after_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '变更后体积(μL)',

  `ref_type` varchar(32) NULL DEFAULT NULL COMMENT '关联业务类型: TASK 调度任务/ORDER 单据/API 外部调用/CHECK 盘点',
  `ref_id` varchar(64) NULL DEFAULT NULL COMMENT '关联业务编号(跨模块只认编码，不建外键)',
  `ref_no` varchar(64) NULL DEFAULT NULL COMMENT '关联业务单号',
  `idempotent_key` varchar(128) NULL DEFAULT NULL COMMENT '幂等键(外部模块调用防重复过账；为空不参与唯一约束)',

  `operator` varchar(64) NULL DEFAULT NULL COMMENT '操作人(手工=登录用户；调度/设备=system 或设备编码)',
  `operator_type` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '操作者类型: USER 人工/DEVICE 设备/AUTO 自动',
  `operate_time` datetime NOT NULL COMMENT '业务操作时间(≠入库时间，支持补录历史)',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注/操作原因(下架不强制填写)',

  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除(流水只增不改，业务侧不提供删除入口)',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_instance`(`instance_id` ASC, `operate_time` ASC) USING BTREE,
  INDEX `idx_root_instance`(`root_instance_id` ASC, `operate_time` ASC) USING BTREE,
  INDEX `idx_from_slot`(`from_slot_id` ASC, `operate_time` ASC) USING BTREE,
  INDEX `idx_to_slot`(`to_slot_id` ASC, `operate_time` ASC) USING BTREE,
  INDEX `idx_operation`(`operation_id` ASC) USING BTREE,
  INDEX `idx_content`(`content_def_code` ASC, `operate_time` ASC) USING BTREE,
  INDEX `idx_operate_time`(`operate_time` ASC) USING BTREE,
  INDEX `idx_movement_type`(`movement_type` ASC) USING BTREE,
  INDEX `idx_ref`(`ref_type` ASC, `ref_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_idempotent`(`tenant_id` ASC, `idempotent_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料流水(只增不改：上架/下架/转移/消耗/状态变更)';

-- =============================================================================
-- 说明:
--
--   1. 一条流水 = 一个业务事实。
--        创建实例            -> CREATE                (to_slot 有值表示建账即入位)
--        未落位 -> 落位       -> PUT_IN                (from 空，to 有值)
--        落位   -> 未落位     -> TAKE_OUT              (from 有值，to 空)
--        落位   -> 另一槽位   -> MOVE                  (from / to 都有值)
--        状态变更             -> STATUS_CHANGE         (只填 before/after_status)
--        消耗                 -> CONSUME               (填 before/change/after 数量或体积 + 状态变化)
--
--   2. 整树转移只记顶层实例一条流水。子实例（如 96 孔）跟随根实例移动，属同一事务，
--      不单独出流水；需要定位时用 root_instance_id 索引。
--
--   3. 消耗支持两种计量，可同时出现:
--        离散计数  qty      24 根离心管用掉 4 根 -> change_qty = -4，4 个子实例转 USED
--        连续体积  volUl    500mL 缓冲液用掉 200mL -> change_vol_ul = -200
--      液体消耗后 volume 归零 -> instance_status 转 USED，否则转 IN_USE。
--
--   4. 下架不引入新状态: 「在不在架上」是位置(root_slot_id)，「能不能用」是状态(instance_status)，
--      两者正交，不用状态去表达位置。下架后实例留在系统（root_slot_id 为空），
--      具体是临时取出还是报废出库，看 instance_status 与流水 remark。
--
--   5. 消耗不改变槽位占用: 槽位占用只看顶层实例的 root_slot_id，
--      消耗子实例只是让盒内格位空出来，盒子仍占着原槽位。
--
--   6. 幂等: uk_idempotent(tenant_id, idempotent_key)。idempotent_key 允许为空
--      （NULL 不参与唯一约束），不需要生成列——因为本表永不逻辑删除。
--
--   7. 冗余 content_def_code / from_zone_code / to_zone_code 是为了按物质、按区域
--      直接筛流水而不 join 实例表与槽位表。
-- =============================================================================

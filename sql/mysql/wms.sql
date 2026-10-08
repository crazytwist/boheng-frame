-- ⚠️ 历史脚本，已被 sql/mysql/wms-final.sql 取代，请勿执行。
-- =============================================================================
-- Lab-WMS 一期 DDL（实验室仓储管理 · 空间 / 物料 / 台账 / 出入库）
-- 版本: v1.4-boheng    底座: boheng-boot-mini (cn.boheng.frame)    目标库: MySQL 8.0+
-- 对应原设计: docs/lab-wms-design.md (v1.1 + §4.5/§4.6)
-- v1.1 空间域修订见差异说明 9-12 条；v1.3 字段收敛见 18-22 条；v1.4 实例域按原设计重写见 23-31 条
--
-- 与原设计的差异（已按「全面对齐 boheng 底座」执行）:
--   1. 表前缀 lab_ -> wms_
--   2. 审计字段对齐 BaseDO: creator / create_time / updater / update_time / deleted(bit 0/1)
--      —— 原 create_id / update_id / del_flag 废弃
--   3. 全表新增 tenant_id，实体继承 TenantBaseDO，由租户拦截器自动隔离
--   4. 布尔位 CHAR(1) T/F -> bit(1)，Java 侧用 Boolean
--   5. status 对齐 CommonStatusEnum：0 开启 / 1 停用，类型 CHAR(1) -> tinyint
--      —— 注意：此处与原设计的「'1' 启用 / '0' 停用」相反，属有意为之（新库无历史数据）
--   6. 唯一键 -> 普通索引；唯一性由 Service 层校验（对齐 boheng 既有做法：
--      逻辑删除 deleted 只有 0/1，无法承载「删除后编码可复用」的唯一约束）
--   7. 主键保持 BIGINT 雪花（应用侧生成，DDL 不使用 AUTO_INCREMENT）
--   8. 不建外键，表间仅通过 编码/ID 引用（跨模块 device/flow 只认编码）
--
-- v1.1 空间域修订（zone / slot 职责回归本质）:
--   9. zone 不再内嵌「可存放物料类型」，改为独立关联表 wms_zone_material_type
--      （区域 × 物料类型，1:N；值域由字典 wms_material_type 约束）
--      理由: 这是「关系」而非「属性」；需支持优先级/数量上限等关系维度扩展，
--            且需支持「按物料类型反查可用区域」（逗号分隔字段无法走索引）
--  10. slot 移除 temp_min / temp_max：温区是区域属性，槽位不再冗余，
--      查询时沿 zone_code 读时推导（避免槽位高频变动下的双写不一致）
--  11. slot 状态拆为两个正交维度:
--        status      可用状态 0 启用 / 1 停用        —— 档案维度，低频变动
--        slot_status 使用状态 FREE/OCCUPIED/LOCKED/CHECKING —— 运行维度，高频变动
--      原 slot_status 中的 DISABLED 与 status 语义重叠，已移除
--  12. slot 的「占用方」不落库: 占用态一律按 wms_material_instance.root_slot_id 反查
--      （v1.2 曾引入 occupied_instance_id / occupied_instance_code，v1.3 已删除，原因见第 18 条）
--
-- v1.2 实例域新增（可移动实物三层模型：类型 / 实例 / 内容物）:
--  13. 新增 wms_container_type 容器类型（板型规格：布局/容量/嵌套能力/复用/设备板型映射）
--      —— 容器类型独立于物料：同一板型可对应多个品牌物料，设备适配认板型不认物料
--  14. wms_material_info 新增 container_type_id（该物料是一种容器时指向板型）
--  15. 新增 wms_material_instance 物料实例（实物个体树，parent_instance_id 无限嵌套）
--      树的边 = parent_instance_id + parent_position_code
--      root_slot_id 冗余「最终落位」且允许为空（运输中/在设备上/临时取出）
--  16. 新增 wms_instance_content 实例内容物（容器内部的物质：液体/散装物，不建实例）
--      与 wms_inventory 分层记账：inventory 管槽位级，content 管容器内，互不重叠
--  17. slot 是设备上的物理位置时: device_code 保留，device_slot_no 更名 device_position_no
--      —— device_position_no 是厂商侧位置标识（协议下发用），与内部 slot_code 职责分离
--
-- v1.3 空间域 / 实例域字段收敛:
--  18. slot 删除 occupied_instance_id / occupied_instance_code:
--      「实物 -> 位置」只能有一个可写方向，真相源是 wms_material_instance.root_slot_id（已有索引）。
--      双向冗余在「整盒移动」时必然双写；且 capacity > 1 时一个 bigint 字段装不下多个实例。
--      改用 occupied_qty（已占标准位数）表达占用程度，与 slot_status 同源维护。
--  19. slot 删除 lock_source / lock_ref_id / lock_time / lock_expire:
--      预留锁属调度域（任务 / 盘点），不是仓储档案。slot 只用 slot_status 的 LOCKED / CHECKING
--      表达「暂不可用」；如需多任务排队竞争、锁过期自动释放或锁审计，二期在调度域建独立锁表。
--  20. wms_material_instance 删除 device_code:
--      「在设备上」统一由 root_slot_id 指向 slot_type = DEVICE 的槽位表达，避免同一事实两个写法。
--  21. wms_material_instance 的 instance_status 移除 LOCKED（锁归调度域，同第 19 条）。
--  22. instance_status 的 IDLE / LOADED 保留为「便捷状态」（本可由子实例与内容物推导），
--      目的是列表页免 join；它以子节点/内容物为唯一真相源，只做展示缓存，不可单独写。
--
-- v1.4 实例域按原设计重写（三张表定稿：容器类型 / 内容物定义 / 物料实例）:
--  23. ★ 位置也是实例: 孔、架的格位是 hierarchy_role = WELL 的实例，不是坐标。
--      因此内容物 1:1 内联在实例上，删除 wms_instance_content，其字段并入本表 content_* 组。
--      理由: 模型统一（「孔」和「15ml 管」结构同构，代码只有一套），且每个孔都能独立挂
--            批次、效期、状态与来源实验 —— 这是样本追溯的核心粒度。
--      代价: 行数膨胀（一块 96 孔板 = 97 行），空孔也建行。
--  24. 新增 wms_content_def 内容物定义: 把「物质属性」（浓度 / CAS / 危险等级 / 供应商货号 /
--      保质期 / 开封效期 / 存储条件）从物料主数据里拆出独立成表。
--      理由: 物质属性与容器几何规格零重叠，混在一张表会有一半字段恒空；且
--            「50mL 的 75% 乙醇瓶」= 容器类型 × 内容物定义，该组合已完整表达采购对象。
--  25. wms_container_type 补齐层级角色与机械字段（对齐原设计）:
--      新增 hierarchy_role(CARRIER/CONTAINER/WELL)、child_type_code、max_vol_ul、
--      well_count / well_rows / well_cols、size_x/y/z_mm、well_spacing_mm、material、spec_json；
--      移除与之重叠的 layout_rows / layout_cols / position_count / capacity_per_pos / capacity_unit。
--  26. 嵌套合法性改为类型层校验: 必须满足 目标容器.child_type_code = 子实例的类型编码，
--      而不是只靠 nestable 一个开关（即原设计 child_type_code 的用法）。
--  27. wms_material_instance 状态枚举对齐原设计六态:
--      AVAILABLE / RESERVED / IN_USE / USED / EXPIRED / DISCARDED，
--      替代 v1.2 的 IDLE / LOADED / CLEANING / SCRAPPED。
--      理由: 能回答「这件现在能不能用」；其中 RESERVED（步骤已申请未取用）与 EXPIRED 是调度依赖的态。
--  28. wms_material_instance 补内容物内联字段: content_def_id / content_def_code / content_type /
--      batch_no / lot_no / current_vol_ul / current_count / concentration；
--      移除 v1.2 的 material_id / batch_id（改为指向内容物定义 + 批次号字符串）。
--  29. wms_material_instance 补实验溯源: source_execution_id / source_node_id
--      （样本来自哪次执行、哪个步骤；跨模块只认编码）。
--  30. wms_material_instance 补日期三件套 received_at / opened_at / expired_at，
--      替代 v1.2 的单一 expiry_date（保质期与开封效期分别起算）。
--  31. 实例不冗余 zone_code: 区域属于槽位而非实例，整盒移动时会变；需要按区查询时
--      用 root_slot_code 关联槽位即可（root_slot_code 本身已是展示冗余）。
--
-- 执行顺序: 容器类型 -> 内容物定义 -> 空间 -> 实例 -> 台账 -> 单据
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. wms_zone_info  区域树（唯一空间主表：实验室/房间/功能区/温区/料架…全部节点）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_zone_info` (
  `id` bigint NOT NULL COMMENT '主键',
  `zone_code` varchar(64) NOT NULL COMMENT '区域编码(租户内唯一)',
  `parent_zone_code` varchar(64) NULL DEFAULT NULL COMMENT '父区域编码(NULL=根节点)',
  `zone_name` varchar(128) NOT NULL COMMENT '区域名称',
  `zone_type` varchar(32) NOT NULL COMMENT '区域类型: LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义',
  `zone_level` int NOT NULL DEFAULT 1 COMMENT '树深度(根=1，冗余，便于范围查询)',
  `temp_min` decimal(5,2) NULL DEFAULT NULL COMMENT '温区下限(℃，沿树继承)',
  `temp_max` decimal(5,2) NULL DEFAULT NULL COMMENT '温区上限(℃，沿树继承)',
  `biosafety_level` int NULL DEFAULT NULL COMMENT '生物安全等级(1-4，沿树继承)',
  `rack_rows` int NULL DEFAULT NULL COMMENT '行数(RACK，批量生成槽位模板)',
  `rack_cols` int NULL DEFAULT NULL COMMENT '列数(RACK，批量生成槽位模板)',
  `slot_capacity` int NULL DEFAULT NULL COMMENT '每槽位标准容量(RACK 模板)',
  `address` varchar(255) NULL DEFAULT NULL COMMENT '物理地址描述(LAB/ROOM)',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0 开启 / 1 停用（CommonStatusEnum）',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_zone_code`(`zone_code` ASC) USING BTREE,
  INDEX `idx_parent_zone_code`(`parent_zone_code` ASC) USING BTREE,
  INDEX `idx_zone_type`(`zone_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '区域树(实验室/房间/功能区/温区/料架统一节点)';

-- -----------------------------------------------------------------------------
-- 2. wms_zone_material_type  区域可存放的物料类型（区域 × 物料类型，1:N）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_zone_material_type` (
  `id` bigint NOT NULL COMMENT '主键',
  `zone_code` varchar(64) NOT NULL COMMENT '区域编码',
  `material_type` varchar(32) NOT NULL COMMENT '物料类型(字典 wms_material_type)',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '优先级(越小越优先，用于上架推荐排序)',
  `max_qty` decimal(18,4) NULL DEFAULT NULL COMMENT '该类型在本区域的数量上限(NULL=不限)',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_zone_code`(`zone_code` ASC) USING BTREE,
  INDEX `idx_material_type`(`material_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '区域可存放的物料类型(区域 × 物料类型)';
-- 语义说明:
--   1. 区域未配置任何行 = 该区域不限制物料类型
--   2. 子区域未配置时，沿 parent_zone_code 逐级向上继承第一个有配置的层级
--   3. zone_code + material_type 唯一性由 Service 层校验（对齐 boheng 惯例，不建 DB 唯一键）
--   4. material_type 与 wms_material_info.material_type 共用同一字典 wms_material_type

-- -----------------------------------------------------------------------------
-- 3. wms_slot_info  槽位（叶子实体：档案 + 可用状态 + 使用状态/占用）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_slot_info` (
  `id` bigint NOT NULL COMMENT '主键',
  `slot_code` varchar(64) NOT NULL COMMENT '槽位编码(租户内唯一)',
  `zone_code` varchar(64) NOT NULL COMMENT '挂载区域编码(唯一从属边，父节点须可挂载且为叶子)',
  `slot_type` varchar(32) NOT NULL COMMENT '槽位类型: STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '可用状态: 0 启用 / 1 停用（档案维度，低频变动，CommonStatusEnum）',
  `slot_status` varchar(16) NOT NULL DEFAULT 'FREE' COMMENT '使用状态: FREE 空闲/OCCUPIED 占用/LOCKED 锁定/CHECKING 盘点中（运行维度，高频变动）',
  `capacity` int NOT NULL DEFAULT 1 COMMENT '容量(标准位)',
  `unique_batch` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否一位一批(样本位/器位)',
  `row_no` int NULL DEFAULT NULL COMMENT '行号',
  `col_no` int NULL DEFAULT NULL COMMENT '列号',
  `layer_no` int NULL DEFAULT NULL COMMENT '层号',
  `device_code` varchar(64) NULL DEFAULT NULL COMMENT '所属设备编码(本槽位是设备上的物理位置时填写，纯货架留空)',
  `device_position_no` varchar(64) NULL DEFAULT NULL COMMENT '设备侧位置标识(厂商命名，如 Tecan Stack-3；协议下发用，与 slot_code 解耦)',
  `occupied_qty` int NOT NULL DEFAULT 0 COMMENT '已占标准位数(容量校验快路径；=0 即 FREE，与 slot_status 由同一业务方法维护)',
  `occupied_time` datetime NULL DEFAULT NULL COMMENT '转为占用态的时间(长期占用告警用)',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_slot_code`(`slot_code` ASC) USING BTREE,
  INDEX `idx_zone_code`(`zone_code` ASC) USING BTREE,
  INDEX `idx_device`(`device_code` ASC, `device_position_no` ASC) USING BTREE,
  INDEX `idx_slot_status`(`slot_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '槽位(叶子: 档案 + 可用状态 + 使用状态/占用)';
-- 说明:
--   1. 温区不落槽位，按 zone_code 向上取第一个配置了 temp_min/temp_max 的区域。
--   2. 「谁占用这个槽位」不落本表: 真相源是 wms_material_instance.root_slot_id（已有索引）。
--      需要占用方时按 root_slot_id 反查，例如 查某槽位上的实例:
--        SELECT * FROM wms_material_instance WHERE root_slot_id = ? AND deleted = b'0';
--   3. slot_status 与 occupied_qty 同源: occupied_qty = 0 <=> FREE，二者必须由同一业务方法一起维护
--      （装卸、移库、盘点调整），不允许只改其中一个。
--   4. 判断「可用槽位」= status = 0 AND slot_status = 'FREE'; 若还要排除临时不可用，
--      再叠加 slot_status NOT IN ('LOCKED','CHECKING')。
--   5. 预留锁的元信息（原 lock_source / lock_ref_id / lock_time / lock_expire）不落本表:
--      锁属调度域，slot 只用 slot_status 的 LOCKED / CHECKING 表达「暂不可用」。
--      代价: 无法在本表回答「谁锁的、何时过期」。二期若需要，在调度域建独立锁表。

-- -----------------------------------------------------------------------------
-- 4. wms_material_info  物料主数据
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_material_info` (
  `id` bigint NOT NULL COMMENT '主键',
  `material_code` varchar(64) NOT NULL COMMENT '物料编码(租户内唯一)',
  `material_name` varchar(128) NOT NULL COMMENT '物料名称',
  `material_type` varchar(32) NOT NULL COMMENT '物料类型(字典 wms_material_type，与 wms_zone_material_type.material_type 同值域)',
  `container_type_id` bigint NULL DEFAULT NULL COMMENT '容器类型编号(该物料是一种容器时指向板型，否则为空)',
  `category_id` bigint NULL DEFAULT NULL COMMENT '分类编号(一期平铺，二期改树)',
  `spec` varchar(128) NULL DEFAULT NULL COMMENT '规格(500mL/100T)',
  `unit` varchar(32) NOT NULL COMMENT '库存单位',
  `storage_cond` varchar(32) NULL DEFAULT NULL COMMENT '存储条件: RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融(与 zone 继承温区做匹配校验)',
  `require_batch` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否批次管理',
  `fefo` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否 FEFO 出库',
  `open_expiry_days` int NULL DEFAULT NULL COMMENT '开瓶效期(天)',
  `hazard_flag` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否危化品',
  `cas_no` varchar(64) NULL DEFAULT NULL COMMENT 'CAS 号(试剂)',
  `manufacturer` varchar(128) NULL DEFAULT NULL COMMENT '生产厂商',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0 开启 / 1 停用（CommonStatusEnum）',
  `description` varchar(1024) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_material_code`(`material_code` ASC) USING BTREE,
  INDEX `idx_material_type`(`material_type` ASC) USING BTREE,
  INDEX `idx_material_name`(`material_name` ASC) USING BTREE,
  INDEX `idx_container_type_id`(`container_type_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料主数据';

-- -----------------------------------------------------------------------------
-- 5. wms_material_batch  物料批次
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_material_batch` (
  `id` bigint NOT NULL COMMENT '主键',
  `material_id` bigint NOT NULL COMMENT '物料编号',
  `batch_no` varchar(64) NOT NULL COMMENT '批号/LOT',
  `batch_status` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '批次状态: NORMAL 正常/FROZEN 冻结隔离/DEPLETED 耗尽/EXPIRED 过期',
  `produce_date` date NULL DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date NULL DEFAULT NULL COMMENT '失效日期(FEFO 依据)',
  `opened_time` datetime NULL DEFAULT NULL COMMENT '开瓶时间',
  `opened_expiry_date` date NULL DEFAULT NULL COMMENT '开瓶失效日期(开瓶后 FEFO 取更早者)',
  `supplier_name` varchar(128) NULL DEFAULT NULL COMMENT '供应商',
  `price` decimal(18,4) NULL DEFAULT NULL COMMENT '单价',
  `sample_ref` varchar(64) NULL DEFAULT NULL COMMENT '样本编码(样本类型: 物理样本唯一标识)',
  `barcode` varchar(128) NULL DEFAULT NULL COMMENT '条码(开瓶标签/样本管条码)',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_material_id_batch_no`(`material_id` ASC, `batch_no` ASC) USING BTREE,
  INDEX `idx_expiry_date`(`expiry_date` ASC) USING BTREE,
  INDEX `idx_opened_expiry_date`(`opened_expiry_date` ASC) USING BTREE,
  INDEX `idx_barcode`(`barcode` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料批次';

-- -----------------------------------------------------------------------------
-- 6. wms_inventory  库存台账（聚合键: slot × batch，唯一库存事实源）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_inventory` (
  `id` bigint NOT NULL COMMENT '主键',
  `slot_id` bigint NOT NULL COMMENT '槽位编号',
  `slot_code` varchar(64) NOT NULL COMMENT '槽位编码(冗余，编码寻址)',
  `material_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint NOT NULL COMMENT '批次编号',
  `qty` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '现存数量(>0；归零即删除该行)',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_slot_id_batch_id`(`slot_id` ASC, `batch_id` ASC) USING BTREE,
  INDEX `idx_material_id`(`material_id` ASC) USING BTREE,
  INDEX `idx_batch_id`(`batch_id` ASC) USING BTREE,
  INDEX `idx_slot_code`(`slot_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存台账(slot × batch)';

-- -----------------------------------------------------------------------------
-- 7. wms_inventory_history  库存流水（只增不改，正反向可追溯）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_inventory_history` (
  `id` bigint NOT NULL COMMENT '主键',
  `slot_id` bigint NOT NULL COMMENT '槽位编号',
  `slot_code` varchar(64) NOT NULL COMMENT '槽位编码',
  `material_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint NOT NULL COMMENT '批次编号',
  `change_type` varchar(32) NOT NULL COMMENT '变更类型: IN 入库/OUT 出库/MOVE_IN 移入/MOVE_OUT 移出/CHECK_ADJ 盘点调整/INIT 建账',
  `before_qty` decimal(18,4) NOT NULL COMMENT '变更前数量',
  `change_qty` decimal(18,4) NOT NULL COMMENT '变更量(正负)',
  `after_qty` decimal(18,4) NOT NULL COMMENT '变更后数量',
  `order_type` varchar(32) NULL DEFAULT NULL COMMENT '关联单据类型: IN/OUT/MOVE/CHECK',
  `order_id` bigint NULL DEFAULT NULL COMMENT '关联单据编号',
  `order_detail_id` bigint NULL DEFAULT NULL COMMENT '关联单据明细编号(幂等键来源)',
  `task_id` bigint NULL DEFAULT NULL COMMENT '关联实验任务编号',
  `operator_type` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '操作者类型: USER/DEVICE/AUTO',
  `operator_ref` varchar(64) NULL DEFAULT NULL COMMENT '操作者标识(设备编码/定时任务标识)',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_detail_change`(`order_detail_id` ASC, `change_type` ASC) USING BTREE,
  INDEX `idx_slot_id_create_time`(`slot_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_batch_id_create_time`(`batch_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存变更流水(只增不改)';

-- -----------------------------------------------------------------------------
-- 8. wms_in_order  入库单
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_in_order` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '单号 RK202609250001(租户内唯一)',
  `order_type` varchar(32) NOT NULL COMMENT '入库类型: PURCHASE 采购/RETURN 归还/RECYCLE 回收/INIT 建账',
  `order_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '单据状态: DRAFT 草稿/CONFIRMED 已确认/EXECUTING 执行中/COMPLETED 已完成/CANCELLED 已取消',
  `source_order` varchar(64) NULL DEFAULT NULL COMMENT '来源单号(采购单/任务编号)',
  `applicant_id` bigint NULL DEFAULT NULL COMMENT '申请人用户编号',
  `execute_time` datetime NULL DEFAULT NULL COMMENT '开始执行时间',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_order_status`(`order_status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '入库单';

-- -----------------------------------------------------------------------------
-- 9. wms_in_order_detail  入库单明细（逐条过账单元）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_in_order_detail` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '入库单编号',
  `material_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint NULL DEFAULT NULL COMMENT '批次编号(执行时生成/绑定)',
  `plan_qty` decimal(18,4) NOT NULL COMMENT '计划数量',
  `executed_qty` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '已执行数量',
  `target_slot_id` bigint NULL DEFAULT NULL COMMENT '建议/实际入库槽位编号',
  `target_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '建议/实际入库槽位编码',
  `detail_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态: PENDING 待执行/EXECUTING 执行中/DONE 已完成/SKIPPED 已跳过',
  `execute_time` datetime NULL DEFAULT NULL COMMENT '执行时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_target_slot_id`(`target_slot_id` ASC) USING BTREE,
  INDEX `idx_detail_status`(`detail_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '入库单明细';

-- -----------------------------------------------------------------------------
-- 10. wms_out_order  出库单
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_out_order` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '单号 CK202609250001(租户内唯一)',
  `order_type` varchar(32) NOT NULL COMMENT '出库类型: USE 领用/EXPERIMENT 实验消耗/SCRAP 报废',
  `order_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '单据状态: DRAFT 草稿/CONFIRMED 已确认/EXECUTING 执行中/COMPLETED 已完成/CANCELLED 已取消',
  `task_id` bigint NULL DEFAULT NULL COMMENT '关联实验任务编号',
  `applicant_id` bigint NULL DEFAULT NULL COMMENT '申请人用户编号',
  `execute_time` datetime NULL DEFAULT NULL COMMENT '开始执行时间',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_order_status`(`order_status` ASC) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '出库单';

-- -----------------------------------------------------------------------------
-- 11. wms_out_order_detail  出库单明细（FEFO 推荐源 + 逐条过账）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_out_order_detail` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '出库单编号',
  `material_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint NULL DEFAULT NULL COMMENT '实际出库批次编号(FEFO 推荐，执行时复核)',
  `plan_qty` decimal(18,4) NOT NULL COMMENT '计划数量',
  `executed_qty` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '已执行数量',
  `source_slot_id` bigint NULL DEFAULT NULL COMMENT '出库槽位编号',
  `source_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '出库槽位编码',
  `detail_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态: PENDING 待执行/EXECUTING 执行中/DONE 已完成/SKIPPED 已跳过',
  `execute_time` datetime NULL DEFAULT NULL COMMENT '执行时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_source_slot_id`(`source_slot_id` ASC) USING BTREE,
  INDEX `idx_detail_status`(`detail_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '出库单明细';

-- -----------------------------------------------------------------------------
-- 12. wms_move_order  移库单（二期启用，一期建表备用）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_move_order` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '单号 YK202609250001(租户内唯一)',
  `order_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '单据状态: DRAFT 草稿/CONFIRMED 已确认/EXECUTING 执行中/COMPLETED 已完成/CANCELLED 已取消',
  `task_id` bigint NULL DEFAULT NULL COMMENT '关联实验任务编号',
  `device_code` varchar(64) NULL DEFAULT NULL COMMENT '涉及设备编码(设备进板/出板时)',
  `applicant_id` bigint NULL DEFAULT NULL COMMENT '申请人用户编号',
  `execute_time` datetime NULL DEFAULT NULL COMMENT '开始执行时间',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_order_status`(`order_status` ASC) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '移库单';

-- -----------------------------------------------------------------------------
-- 13. wms_move_order_detail  移库单明细
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_move_order_detail` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '移库单编号',
  `material_id` bigint NOT NULL COMMENT '物料编号',
  `batch_id` bigint NULL DEFAULT NULL COMMENT '批次编号',
  `qty` decimal(18,4) NOT NULL COMMENT '移库数量',
  `from_slot_id` bigint NULL DEFAULT NULL COMMENT '移出槽位编号',
  `from_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '移出槽位编码',
  `to_slot_id` bigint NULL DEFAULT NULL COMMENT '移入槽位编号',
  `to_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '移入槽位编码',
  `move_reason` varchar(32) NOT NULL DEFAULT 'RELOCATE' COMMENT '移库原因: RELOCATE 整理/TASK_RETURN 任务回库/DEVICE_IN 设备进板/DEVICE_OUT 设备出板/TEMP_OUT 临时取出',
  `detail_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态: PENDING 待执行/EXECUTING 执行中/DONE 已完成/SKIPPED 已跳过',
  `execute_time` datetime NULL DEFAULT NULL COMMENT '执行时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_from_slot_id`(`from_slot_id` ASC) USING BTREE,
  INDEX `idx_to_slot_id`(`to_slot_id` ASC) USING BTREE,
  INDEX `idx_detail_status`(`detail_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '移库单明细';

-- -----------------------------------------------------------------------------
-- 14. wms_check_order  盘点单（二期启用，一期建表备用）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_check_order` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '单号 PD202609250001(租户内唯一)',
  `check_scope` varchar(32) NOT NULL DEFAULT 'FULL' COMMENT '盘点范围: FULL 全部/ZONE 按区域/RACK 按料架',
  `scope_code` varchar(64) NULL DEFAULT NULL COMMENT '范围区域编码(FULL 时为空)',
  `order_status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '单据状态: DRAFT 草稿/CHECKING 盘点中/CONFIRMED 已确认/CANCELLED 已取消',
  `check_time` datetime NULL DEFAULT NULL COMMENT '盘点时间',
  `diff_count` int NOT NULL DEFAULT 0 COMMENT '差异条数(冗余)',
  `applicant_id` bigint NULL DEFAULT NULL COMMENT '申请人用户编号',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_order_status`(`order_status` ASC) USING BTREE,
  INDEX `idx_check_scope_scope_code`(`check_scope` ASC, `scope_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '盘点单';

-- -----------------------------------------------------------------------------
-- 15. wms_check_order_detail  盘点单明细（差异确认后生成 CHECK_ADJ 流水）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_check_order_detail` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '盘点单编号',
  `slot_id` bigint NOT NULL COMMENT '槽位编号',
  `slot_code` varchar(64) NOT NULL COMMENT '槽位编码',
  `material_id` bigint NULL DEFAULT NULL COMMENT '物料编号',
  `batch_id` bigint NULL DEFAULT NULL COMMENT '批次编号',
  `book_qty` decimal(18,4) NOT NULL DEFAULT 0 COMMENT '账面数量',
  `actual_qty` decimal(18,4) NULL DEFAULT NULL COMMENT '实盘数量(NULL=未盘)',
  `diff_qty` decimal(18,4) NULL DEFAULT NULL COMMENT '差异数量(= 实盘 - 账面)',
  `handle_type` varchar(32) NULL DEFAULT NULL COMMENT '处理方式: ADJUST 调整/IGNORE 忽略',
  `detail_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态: PENDING 待盘/COUNTED 已盘/CONFIRMED 已确认',
  `remark` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_slot_id`(`slot_id` ASC) USING BTREE,
  INDEX `idx_detail_status`(`detail_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '盘点单明细';

-- =============================================================================
-- v1.2 新增 / v1.4 按原设计重写：实例域（可移动实物 —— 空间域表达不了的那一层）
--   16. wms_container_type     容器类型（规格模板：几何 / 容量 / 层级角色 / 设备映射）
--   17. wms_content_def        内容物定义（试剂 / 标准品 / 缓冲液 / 样本的物质属性）
--   18. wms_material_instance  物料实例（实物个体树：位置即实例 + 内容物内联）
--
-- 三张表的分工（v1.4 定稿）:
--   容器类型   管「能装什么」   —— 几何规格 + 层级角色 + 承载的子单元类型
--   内容物定义 管「物质是什么」 —— 浓度 / CAS / 危险等级 / 保质期 / 开封效期 / 存储条件
--   物料实例   管「这一件实物」 —— 规格绑定 + 内容绑定 + 树层级 + 物理落位
--
-- ★ v1.4 核心决策：位置也是实例
--   「孔」「架的格位」不是坐标，而是 hierarchy_role = WELL 的实例。
--   因此内容物可以 1:1 内联在实例上，不需要独立的「实例内容物」表
--   （v1.2 的 wms_instance_content 已在 v1.4 删除，字段并入实例表的 content_* 组）。
--   一块 96 孔板 = 1 行载体实例 + 96 行孔实例；空孔同样建行，但 content_type = EMPTY。
--
-- 与已有表的分工:
--   wms_zone_info / wms_slot_info  → 空间的位置（架子第 3 格、设备第 3 位）
--   wms_material_instance          → 可移动的实物个体（盒、板、管、瓶，含孔）
--   wms_inventory                  → 槽位级的数量账
--
-- 两套「位置」不要混淆:
--   slot                  是空间位置（架子第 3 格）—— 只有顶层实例才落 slot
--   parent_position_code  是容器内位置（板子的 A1 孔）—— 它是子实例的位置码，本身也是实例
--   一块 96 孔板放在料架上占 1 个 slot；它内部的 96 个孔是子实例，不落 slot 表
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 16. wms_container_type  容器类型（规格模板：几何 / 容量 / 层级角色 / 设备映射）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_container_type` (
  `id` bigint NOT NULL COMMENT '主键',
  `type_code` varchar(64) NOT NULL COMMENT '容器类型编码(租户内唯一)，如 PLATE_96_WELL / TUBE_EP_15ML / BOTTLE_50ML',
  `type_name` varchar(128) NOT NULL COMMENT '容器类型名称，如 96孔板 / 15ml EP管 / 50ml试剂瓶',
  `category` varchar(32) NOT NULL COMMENT '物理形态: PLATE 孔板/TUBE 试管离心管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他',
  `hierarchy_role` varchar(32) NOT NULL COMMENT '层级角色: CARRIER 载体(承载其他容器，如托盘/孔板/试管架)/CONTAINER 直接容器(直接装内容物，如试管/瓶)/WELL 孔位(孔板与架的最小单元)',
  `max_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '最大容积(μL，1ml=1000)；CARRIER 不直接装物质，此列为空；固体或计数类容器也留空',
  `well_count` int NULL DEFAULT NULL COMMENT '位数(仅 CARRIER)，如 96孔板=96 / 24孔试管架=24',
  `well_rows` int NULL DEFAULT NULL COMMENT '行数(仅 CARRIER)，如 96孔板=8 / 24孔架=4',
  `well_cols` int NULL DEFAULT NULL COMMENT '列数(仅 CARRIER)，如 96孔板=12 / 24孔架=6',
  `position_naming` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '位置命名规则(位置码的生成与校验): ROW_COL 如 A1/B12 | SEQ 如 1..96 | ROW_COL_LAYER 如 A1-L1 | NONE 无位置概念',
  `child_type_code` varchar(64) NULL DEFAULT NULL COMMENT '承载的子单元类型编码(仅 CARRIER): 孔板->WELL_STANDARD / 24孔架->TUBE_EP_15ML；嵌套合法性在类型层校验',
  `size_x_mm` decimal(8,2) NULL DEFAULT NULL COMMENT '外形尺寸 X(mm)，机械臂抓取用',
  `size_y_mm` decimal(8,2) NULL DEFAULT NULL COMMENT '外形尺寸 Y(mm)',
  `size_z_mm` decimal(8,2) NULL DEFAULT NULL COMMENT '外形尺寸 Z(mm，高度)',
  `well_spacing_mm` decimal(6,2) NULL DEFAULT NULL COMMENT '孔间距(mm)，多通道移液与坐标换算用',
  `material` varchar(64) NULL DEFAULT NULL COMMENT '材质: PP / PC / PS / GLASS / PTFE 等',
  `spec_json` json NULL DEFAULT NULL COMMENT '其他规格参数 JSON，如 {"color":"transparent","sterile":true}',
  `nestable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否允许被装入其他容器(作为子实例)；与 hierarchy_role 正交，用于显式禁止某些大件入架',
  `max_nest_depth` int NULL DEFAULT NULL COMMENT '该类型内部允许的最大嵌套层数(NULL=不限，应用层同时受 MAX_INSTANCE_DEPTH=16 约束)',
  `usage_type` varchar(16) NOT NULL DEFAULT 'REUSABLE' COMMENT '使用类型: REUSABLE 周转复用/DISPOSABLE 一次性耗材',
  `life_cycles` int NULL DEFAULT NULL COMMENT '复用次数上限(NULL=不限，仅 REUSABLE 有效)',
  `temp_min` decimal(5,2) NULL DEFAULT NULL COMMENT '物理耐受温区下限(℃，用于校验能否进低温区)',
  `temp_max` decimal(5,2) NULL DEFAULT NULL COMMENT '物理耐受温区上限(℃，用于校验能否进高温区)',
  `device_labware_name` varchar(128) NULL DEFAULT NULL COMMENT '设备侧板型名称(如 Tecan i-control labware 名，设备适配映射点)',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0 开启 / 1 停用（CommonStatusEnum）',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type_code`(`type_code` ASC) USING BTREE,
  INDEX `idx_category_role`(`category` ASC, `hierarchy_role` ASC) USING BTREE,
  INDEX `idx_child_type_code`(`child_type_code` ASC) USING BTREE,
  INDEX `idx_device_labware_name`(`device_labware_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '容器类型(规格模板：几何/容量/层级角色/设备映射)';
-- 说明:
--   1. category 与 hierarchy_role 是两个正交维度：category 是物理形态（它是根管子），
--      hierarchy_role 是层级角色（它直接装物质）。前者用于展示与筛选，后者用于嵌套校验。
--   2. 容量只给「直接装物质」的角色：CONTAINER / WELL 填 max_vol_ul，CARRIER 留空。
--   3. child_type_code 是嵌套合法性的唯一依据：把 A 装进 B 时必须满足 B.child_type_code = A.type_code，
--      并由 Service 层同时校验 A.nestable = 1、层数不超过 B.max_nest_depth 与全局 MAX_INSTANCE_DEPTH=16。
--   4. position_naming 决定 position_code 的生成与校验规则（ROW_COL 生成 A1..H12；SEQ 生成 1..96）。
--   5. device_labware_name 为单设备映射；同一板型需对接多台不同厂商设备时，二期拆映射表。
--   6. 本表只描述「容器」；内容物的物质属性一律在 wms_content_def，两者不互相掺杂。

-- -----------------------------------------------------------------------------
-- 17. wms_content_def  内容物定义（试剂 / 标准品 / 缓冲液 / 样本的物质属性）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_content_def` (
  `id` bigint NOT NULL COMMENT '主键',
  `content_code` varchar(64) NOT NULL COMMENT '内容物编码(租户内唯一)，如 PH-BUFFER-7 / ETHANOL-75PCT',
  `content_name` varchar(128) NOT NULL COMMENT '内容物名称，如 pH7标准缓冲液 / 75%乙醇',
  `content_type` varchar(32) NOT NULL COMMENT '内容物类型: REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他',
  `unit` varchar(16) NOT NULL COMMENT '计量单位: UL/ML/MG/G/COUNT',
  `supplier` varchar(128) NULL DEFAULT NULL COMMENT '供应商名称',
  `catalog_no` varchar(64) NULL DEFAULT NULL COMMENT '供应商货号/目录号',
  `cas_no` varchar(32) NULL DEFAULT NULL COMMENT 'CAS 号(化学物质标识，危险品管控用)',
  `concentration` varchar(64) NULL DEFAULT NULL COMMENT '标准浓度描述，如 1mol/L / 75% / pH7.0',
  `storage_cond` varchar(32) NULL DEFAULT NULL COMMENT '存储条件(与 zone 继承温区做程序化匹配): RT 常温/C2_8 2~8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融',
  `shelf_life_days` int NULL DEFAULT NULL COMMENT '保质期(天)，自入库日期计算，NULL=不限',
  `open_life_days` int NULL DEFAULT NULL COMMENT '开封后有效期(天)，自开封日期计算，NULL=不限',
  `hazard_level` varchar(16) NULL DEFAULT NULL COMMENT '危险品等级: NONE 无/LOW 低危/MEDIUM 中危/HIGH 高危/FLAMMABLE 易燃/TOXIC 有毒',
  `require_batch` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否批次管理',
  `fefo` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否 FEFO 出库(先到期先出)',
  `spec_json` json NULL DEFAULT NULL COMMENT '其他规格参数 JSON，如 {"purity":"≥99%","grade":"AR"}',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0 开启 / 1 停用（CommonStatusEnum）',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_content_code`(`content_code` ASC) USING BTREE,
  INDEX `idx_content_type`(`content_type` ASC) USING BTREE,
  INDEX `idx_cas_no`(`cas_no` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '内容物定义(试剂/标准品/缓冲液/样本的物质属性)';
-- 说明:
--   1. 本表只描述「物质」，不描述「包装」：容器的一切规格在 wms_container_type。
--      「50mL 的 75% 乙醇瓶」= wms_container_type(BOTTLE_50ML) × 本表(ETHANOL-75PCT)，
--      这一组合已完整表达一个可采购、可入库的对象，不再需要额外的物料主数据表。
--   2. 一行本表可被任意多个实例引用（同一批乙醇分装进 10 个瓶 = 10 个实例指向同一内容物）。
--   3. storage_cond 用枚举而非自由文本，是为了与 zone 继承下来的 temp_min / temp_max
--      做程序化匹配校验（「这批试剂能不能放进这个温区」）；更细的自由描述放 spec_json。
--   4. 保质期与开封效期在本表声明规则，实际到期日落在实例上
--      （received_at + shelf_life_days、opened_at + open_life_days），
--      因为同一内容物的不同实例入库/开封时间各不相同。

-- -----------------------------------------------------------------------------
-- 18. wms_material_instance  物料实例（实物个体树：位置即实例 + 内容物内联）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_material_instance` (
  `id` bigint NOT NULL COMMENT '主键',
  `instance_code` varchar(64) NOT NULL COMMENT '实例编码(租户内唯一，系统按 类型编码+流水号 生成，如 PLATE96-000123)',
  `instance_name` varchar(128) NULL DEFAULT NULL COMMENT '实例名称/别名',
  `barcode` varchar(128) NULL DEFAULT NULL COMMENT '条码/二维码(扫描录入，录入后覆盖系统编码的展示)',
  `container_type_id` bigint NOT NULL COMMENT '容器类型编号',
  `container_type_code` varchar(64) NOT NULL COMMENT '容器类型编码(冗余，编码寻址)',
  `parent_instance_id` bigint NULL DEFAULT NULL COMMENT '父实例编号(NULL=顶层实例)',
  `parent_position_code` varchar(32) NULL DEFAULT NULL COMMENT '在父容器中的位置(如 A1；顶层或父容器无位置概念时为空)',
  `instance_path` varchar(512) NOT NULL DEFAULT '' COMMENT '物化路径(/根实例ID/.../本实例ID，支持 LIKE 前缀查子树)',
  `depth` int NOT NULL DEFAULT 1 COMMENT '嵌套深度(顶层=1，应用层上限 MAX_INSTANCE_DEPTH=16)',
  `root_instance_id` bigint NULL DEFAULT NULL COMMENT '根实例编号(顶层实例填自身；冗余，便于整树查询)',
  `root_slot_id` bigint NULL DEFAULT NULL COMMENT '最终物理落位槽位编号(NULL=未落位: 运输中/在设备上/临时取出)；只有顶层实例有值',
  `root_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '最终物理落位槽位编码(冗余，列表展示免 join)',
  `content_def_id` bigint NULL DEFAULT NULL COMMENT '内容物定义编号(仅 CARRIER 与空容器为空)',
  `content_def_code` varchar(64) NULL DEFAULT NULL COMMENT '内容物编码(冗余，编码寻址)',
  `content_type` varchar(32) NULL DEFAULT NULL COMMENT '内容物类型快照(CARRIER 为 NULL；空容器为 EMPTY；其余同 wms_content_def.content_type，用于免 join 筛选)',
  `batch_no` varchar(64) NULL DEFAULT NULL COMMENT '内部入库批次号',
  `lot_no` varchar(64) NULL DEFAULT NULL COMMENT '厂商原始批号(Lot Number，与内部批次号 batch_no 区分)',
  `current_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '当前体积(μL)，液体类内容物填写，消耗后更新',
  `current_count` int NULL DEFAULT NULL COMMENT '当前数量(个)，固体/耗材类填写，消耗后更新',
  `concentration` varchar(64) NULL DEFAULT NULL COMMENT '当前浓度描述(覆盖内容物定义的标准浓度，如稀释后的实际浓度)',
  `instance_status` varchar(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '实例状态: AVAILABLE 可用/RESERVED 已预留(步骤申请未取用)/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃',
  `received_at` date NULL DEFAULT NULL COMMENT '入库日期(保质期起算)',
  `opened_at` date NULL DEFAULT NULL COMMENT '开封日期(开封后有效期起算，未开封为空)',
  `expired_at` date NULL DEFAULT NULL COMMENT '过期日期(入库日期+保质期，到期置 EXPIRED)',
  `reuse_count` int NOT NULL DEFAULT 0 COMMENT '已复用次数(usage_type=REUSABLE 时累计)',
  `last_clean_time` datetime NULL DEFAULT NULL COMMENT '上次清洗时间',
  `source_execution_id` varchar(64) NULL DEFAULT NULL COMMENT '来源流程执行编号(样本类填写，追踪是哪次实验产生；跨模块只认编码)',
  `source_node_id` varchar(64) NULL DEFAULT NULL COMMENT '来源步骤节点编号(配合 source_execution_id 精确定位到步骤)',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_instance_code`(`instance_code` ASC) USING BTREE,
  INDEX `idx_barcode`(`barcode` ASC) USING BTREE,
  INDEX `idx_container_type_id`(`container_type_id` ASC) USING BTREE,
  INDEX `idx_parent_instance_id`(`parent_instance_id` ASC) USING BTREE,
  INDEX `idx_root_instance_id`(`root_instance_id` ASC) USING BTREE,
  INDEX `idx_root_slot_id`(`root_slot_id` ASC) USING BTREE,
  INDEX `idx_instance_path`(`instance_path`(64) ASC) USING BTREE,
  INDEX `idx_instance_status`(`instance_status` ASC) USING BTREE,
  INDEX `idx_content_def_id`(`content_def_id` ASC) USING BTREE,
  INDEX `idx_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_expired`(`expired_at` ASC, `instance_status` ASC) USING BTREE,
  INDEX `idx_source_execution`(`source_execution_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料实例(实物个体树：位置即实例 + 内容物内联)';
-- 说明（v1.4 重写，对齐原设计）:
--   1. ★ 位置也是实例: hierarchy_role = WELL 的孔/位同样是本表的一行，
--      所以内容物可以 1:1 内联（content_* 组），不需要独立的实例内容物表。
--      一块 96 孔板 = 1 行载体实例 + 96 行孔实例；空孔同样建行，content_type = EMPTY。
--      CARRIER 自身不装物质，content_* 整组为空。
--   2. 三组绑定:
--      规格 <- wms_container_type  container_type_id / container_type_code（必填）
--      内容 <- wms_content_def     content_def_id / content_def_code（CARRIER、空容器为空）
--      落位 -> wms_slot_info       root_slot_id / root_slot_code（可空）
--   3. 树的边 = parent_instance_id + parent_position_code；顶层实例 parent_instance_id 为 NULL。
--      「在设备上」由 root_slot_id 指向 slot_type = DEVICE 的槽位表达，本表不设 device_code。
--   4. 无「启用 / 停用」状态位：实例的终态是 DISCARDED（报废），不是停用，故不设 status 列。
--   5. root_slot_id / root_slot_code / root_instance_id / instance_path / depth 是整树冗余，
--      用来「一次查询定位」，避免递归。移动整棵子树时必须用一条路径前缀 UPDATE 维护子孙，
--      例如把整盒搬到新槽位（这条语句是整盒移动的唯一写入口，禁止绕过它单改某一层）:
--        UPDATE wms_material_instance
--           SET root_slot_id = ?, root_slot_code = ?
--         WHERE instance_path LIKE CONCAT(?, '%') AND deleted = b'0';
--      跨父搬迁还需同步重写 instance_path 与 depth（同一事务内完成）。
--   6. instance_path 走前缀索引(64): 支持 LIKE '/1001/1005/%' 子树查询。
--   7. content_type = EMPTY 表示「能装但现在是空的」；CARRIER 此列为 NULL 表示「不装东西」。
--      两者语义不同，不要互相替代。
--   8. 同级同位唯一: (parent_instance_id, parent_position_code) 由 Service 层校验。
--   9. 不允许删除仍有子节点的实例（否则路径断裂）；嵌套成环由深度上限 + 祖先链校验防护。
--  10. instance_status 只回答「这一件现在能不能用」；锁归调度域，本表只保留 RESERVED
--      这一个「被步骤占用」的语义（步骤已申请、尚未取用）。
--  11. current_vol_ul 与 current_count 二选一填写（液体填体积、固体/计数填数量），
--      两者都不是库存真相源；库存账在 wms_inventory（本轮未重审，见文末待定项）。
--  12. 过期不靠定时任务强推：expired_at < CURDATE() 且 instance_status = AVAILABLE 即视为过期，
--      定时任务只负责把状态刷成 EXPIRED，便于走索引筛选。
--  13. 一期约定 1 个实例占 1 个标准位（供 slot.occupied_qty 累加）；跨位实例二期再引入 capacity_units。

-- =============================================================================
-- 本轮（v1.4）未动，但已被上述三张表影响、需后续轮次决策的表:
--
--   wms_material_info      上一版把它当作「兼内容物定义与容器身份」的物料主数据。
--                          v1.4 已拆出 wms_content_def，且实例不再引用它
--                          （material_id / container_type_id 均已移除）。
--                          待定: 是彻底退场，还是收敛为「采购 / 出入库记账单元」
--                          （N:1 指向 wms_content_def）。
--
--   wms_material_batch     批次信息现已落在实例上（batch_no / lot_no / received_at /
--                          opened_at / expired_at），本表是否还有存在必要需重新评估。
--
--   wms_zone_material_type 其 material_type 用的字典 wms_material_type
--                          (REAGENT/CONSUMABLE/SAMPLE/STANDARD/TOOL) 与
--                          wms_content_def.content_type 值域高度重合，需统一为同一套，
--                          否则会出现两套物料类型值域。
--
--   wms_inventory          聚合键是 slot × batch。改为「位置即实例」后，「库存」的对象
--                          其实是一组实例，聚合键与新模型的关系需要重新推导（本轮不擅自改）。
--
--   Java 侧                 按「先定表结构、暂不写代码」的约定，本轮只改 SQL。
--                          boheng-module-wms 现有代码与生成器仍停留在 v1.2 字段，
--                          下次写代码时必须连同 wms_codegen.py 一次性同步。
-- =============================================================================

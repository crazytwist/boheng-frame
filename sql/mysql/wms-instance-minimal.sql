-- =============================================================================
-- Lab-WMS · 物料实例层（最简版，收敛为 5 张表）
-- 版本: v1.4-minimal    底座: boheng-boot-mini (cn.boheng.frame)    目标库: MySQL 8.0+
-- 来源: 由 wms.sql v1.4 收敛而来
--
-- 收敛原则:
--   1. 只保留 5 张表:
--        空间:  wms_zone_info(区域树) + wms_slot_info(槽位叶子)
--        实例:  wms_container_type(容器类型) + wms_content_def(内容物定义) + wms_material_instance(物料实例)
--      其余表(物料主数据、批次、台账、出入库单据、区域物料类型约束)全部移除
--   2. 「记录」类字段全部移除: 溯源(source_*)、批次(batch_no/lot_no)、
--      复用次数(reuse_count)、清洗时间(last_clean_time)、入库/开封/过期日期(received_at/opened_at/expired_at)
--      以及规格侧的机械/温区/设备映射字段(size_*、well_spacing、material、max_nest_depth、temp_*、device_labware_name)
--   3. 保留的核心能力:
--      - 空间: zone 可配置树 + slot 双状态正交(FREE/OCCUPIED/LOCKED/CHECKING)
--      - 实例: 可无限嵌套的树(parent_instance_id + parent_position_code)
--      - 位置即实例(WELL 孔/位也是实例，内容物 1:1 内联)
--      - 实例绑定容器类型(规格) + 内容物定义(物质)，六态 AVAILABLE/RESERVED/IN_USE/USED/EXPIRED/DISCARDED
--      - 顶层实例可落位 slot(root_slot_id/root_slot_code，仅顶层占用，内部 WELL 不落 slot)
--   4. 保留 instance_status 六态(回答「这一件现在能不能用」)，但移除 RESERVED 之外的调度语义字段
--   5. boheng 底座规范不变: BaseDO 审计字段 + tenant_id 多租户 + 雪花主键 + 无 DB 唯一键
--
-- 已移除: wms_zone_material_type(区域物料类型约束)——其 material_type 值域已与
--   wms_content_def.content_type 脱节，区域约束能力整体后置到后续阶段
--
-- 五张表的分工:
--   区域树     管「空间层级」   —— zone_code/parent_zone_code 树 + 温区/生物安全沿树继承
--   槽位       管「空间叶子」   —— 可用状态(status) + 使用状态(slot_status) + 占用量
--   容器类型   管「能装什么」   —— 几何规格 + 层级角色(CARRIER/CONTAINER/WELL) + 承载子单元
--   内容物定义 管「物质是什么」 —— 浓度 / CAS / 危险等级 / 保质期规则 / 存储条件
--   物料实例   管「这一件实物」 —— 规格绑定 + 内容绑定 + 树层级 + 顶层落位
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. wms_zone_info  区域树（实验室/房间/功能区/温区/料架统一节点）
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
  `ext_data` json NULL DEFAULT NULL COMMENT '扩展数据(JSON): {"rack_rows":8,"rack_cols":12,"slot_capacity":1,"address":"A栋3楼"} 等 RACK 批量生成模板参数与物理地址描述',
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
-- 2. wms_slot_info  槽位（叶子实体：档案 + 可用状态 + 使用状态/占用）
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
  `ext_data` json NULL DEFAULT NULL COMMENT '扩展数据(JSON): {"row_no":3,"col_no":2,"layer_no":1} 等坐标描述',
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
--   5. 预留锁的元信息不落本表: 锁属调度域，slot 只用 slot_status 的 LOCKED/CHECKING 表达「暂不可用」。

-- -----------------------------------------------------------------------------
-- 3. wms_container_type  容器类型（规格模板：几何 / 容量 / 层级角色 / 设备映射）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_container_type` (
  `id` bigint NOT NULL COMMENT '主键',
  `type_code` varchar(64) NOT NULL COMMENT '容器类型编码(租户内唯一)，如 PLATE_96_WELL / TUBE_EP_15ML / BOTTLE_50ML',
  `type_name` varchar(128) NOT NULL COMMENT '容器类型名称，如 96孔板 / 15ml EP管 / 50ml试剂瓶',
  `image_url` varchar(512) NULL DEFAULT NULL COMMENT '图片地址(可空，前端展示默认图)',
  `category` varchar(32) NOT NULL COMMENT '物理形态: PLATE 孔板/TUBE 试管离心管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他',
  `hierarchy_role` varchar(32) NOT NULL COMMENT '层级角色: CARRIER 载体(承载其他容器，如托盘/孔板/试管架)/CONTAINER 直接容器(直接装内容物，如试管/瓶)/WELL 孔位(孔板与架的最小单元)',
  `max_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '最大容积(μL，1ml=1000)；CARRIER 不直接装物质，此列为空；固体或计数类容器也留空',
  `well_count` int NULL DEFAULT NULL COMMENT '位数(仅 CARRIER)，如 96孔板=96 / 24孔试管架=24',
  `well_rows` int NULL DEFAULT NULL COMMENT '行数(仅 CARRIER)，如 96孔板=8 / 24孔架=4',
  `well_cols` int NULL DEFAULT NULL COMMENT '列数(仅 CARRIER)，如 96孔板=12 / 24孔架=6',
  `position_naming` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '位置命名规则(位置码的生成与校验): ROW_COL 如 A1/B12 | SEQ 如 1..96 | ROW_COL_LAYER 如 A1-L1 | NONE 无位置概念',
  `child_type_code` varchar(64) NULL DEFAULT NULL COMMENT '承载的子单元类型编码(仅 CARRIER): 孔板->WELL_STANDARD / 24孔架->TUBE_EP_15ML；嵌套合法性在类型层校验',
  `spec_json` json NULL DEFAULT NULL COMMENT '其他规格参数 JSON，如 {"color":"transparent","sterile":true}',
  `nestable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否允许被装入其他容器(作为子实例)；与 hierarchy_role 正交，用于显式禁止某些大件入架',
  `usage_type` varchar(16) NOT NULL DEFAULT 'REUSABLE' COMMENT '使用类型: REUSABLE 周转复用/DISPOSABLE 一次性耗材',
  `life_cycles` int NULL DEFAULT NULL COMMENT '复用次数上限(NULL=不限，仅 REUSABLE 有效)',
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
  INDEX `idx_child_type_code`(`child_type_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '容器类型(规格模板：几何/容量/层级角色/设备映射)';

-- -----------------------------------------------------------------------------
-- 4. wms_content_def  内容物定义（试剂 / 标准品 / 缓冲液 / 样本的物质属性）
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_content_def` (
  `id` bigint NOT NULL COMMENT '主键',
  `content_code` varchar(64) NOT NULL COMMENT '内容物编码(租户内唯一)，如 PH-BUFFER-7 / ETHANOL-75PCT',
  `content_name` varchar(128) NOT NULL COMMENT '内容物名称，如 pH7标准缓冲液 / 75%乙醇',
  `image_url` varchar(512) NULL DEFAULT NULL COMMENT '图片地址(可空，前端展示默认图)',
  `content_type` varchar(32) NOT NULL COMMENT '内容物类型: REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他',
  `unit` varchar(16) NOT NULL COMMENT '计量单位: UL/ML/MG/G/COUNT',
  `supplier` varchar(128) NULL DEFAULT NULL COMMENT '供应商名称',
  `catalog_no` varchar(64) NULL DEFAULT NULL COMMENT '供应商货号/目录号',
  `cas_no` varchar(32) NULL DEFAULT NULL COMMENT 'CAS 号(化学物质标识，危险品管控用)',
  `concentration` varchar(64) NULL DEFAULT NULL COMMENT '标准浓度描述，如 1mol/L / 75% / pH7.0',
  `storage_cond` varchar(32) NULL DEFAULT NULL COMMENT '存储条件: RT 常温/C2_8 2~8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融',
  `shelf_life_days` int NULL DEFAULT NULL COMMENT '保质期(天)，NULL=不限',
  `open_life_days` int NULL DEFAULT NULL COMMENT '开封后有效期(天)，NULL=不限',
  `hazard_level` varchar(16) NULL DEFAULT NULL COMMENT '危险品等级: NONE 无/LOW 低危/MEDIUM 中危/HIGH 高危/FLAMMABLE 易燃/TOXIC 有毒',
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

-- -----------------------------------------------------------------------------
-- 5. wms_material_instance  物料实例（实物个体树：位置即实例 + 内容物内联 + 顶层落位）
--    —— 最简版：移除溯源、批次、复用、日期等「记录」字段，保留树结构 + 内容物内联 + 落位绑定
-- -----------------------------------------------------------------------------
CREATE TABLE `wms_material_instance` (
  `id` bigint NOT NULL COMMENT '主键',
  `instance_code` varchar(64) NOT NULL COMMENT '实例编码(租户内唯一，系统按 类型编码+流水号 生成，如 PLATE96-000123)',
  `instance_name` varchar(128) NULL DEFAULT NULL COMMENT '实例名称/别名',
  `barcode` varchar(128) NULL DEFAULT NULL COMMENT '条码/二维码(扫描录入)',
  `container_type_id` bigint NOT NULL COMMENT '容器类型编号',
  `container_type_code` varchar(64) NOT NULL COMMENT '容器类型编码(冗余，编码寻址)',
  `parent_instance_id` bigint NULL DEFAULT NULL COMMENT '父实例编号(NULL=顶层实例)',
  `parent_position_code` varchar(32) NULL DEFAULT NULL COMMENT '在父容器中的位置(如 A1；顶层或父容器无位置概念时为空)',
  `instance_path` varchar(512) NOT NULL DEFAULT '' COMMENT '物化路径(/根实例ID/.../本实例ID，支持 LIKE 前缀查子树)',
  `root_instance_id` bigint NULL DEFAULT NULL COMMENT '根实例编号(顶层实例填自身；冗余，便于整树查询)',
  `root_slot_id` bigint NULL DEFAULT NULL COMMENT '落位槽位编号(仅顶层实例占用 slot；未落位为 NULL；内部 WELL 子实例不落 slot)',
  `root_slot_code` varchar(64) NULL DEFAULT NULL COMMENT '落位槽位编码(冗余，编码寻址；与 root_slot_id 同源)',
  `content_def_id` bigint NULL DEFAULT NULL COMMENT '内容物定义编号(仅 CARRIER 与空容器为空)',
  `content_def_code` varchar(64) NULL DEFAULT NULL COMMENT '内容物编码(冗余，编码寻址)',
  `content_type` varchar(32) NULL DEFAULT NULL COMMENT '内容物类型快照(CARRIER 为 NULL；空容器为 EMPTY；其余同 wms_content_def.content_type)',
  `current_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '当前体积(μL)，液体类内容物填写',
  `current_count` int NULL DEFAULT NULL COMMENT '当前数量(个)，固体/耗材类填写',
  `ext_data` json NULL DEFAULT NULL COMMENT '扩展数据(JSON): {"concentration":"0.5mol/L"} 等实例级覆盖描述',
  `instance_status` varchar(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '实例状态: AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `slot_unique_key` bigint GENERATED ALWAYS AS (if((`deleted` = b'0'),`root_slot_id`,NULL)) VIRTUAL COMMENT '槽位唯一占位键(生成列)：未删除且已落位时=root_slot_id，否则 NULL，用于唯一约束',
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
  UNIQUE INDEX `uk_root_slot`(`tenant_id` ASC, `slot_unique_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '物料实例(实物个体树：位置即实例 + 内容物内联)';

-- =============================================================================
-- 说明（最简版 · 5 张表）:
--   [空间] wms_zone_info / wms_slot_info
--   [实例] wms_container_type / wms_content_def / wms_material_instance
--
--   1. 空间: zone 是可配置树(zone_code/parent_zone_code)；slot 是叶子，zone_code 挂载。
--      温区、生物安全沿树继承；slot 不冗余温区。
--   2. 位置也是实例: hierarchy_role = WELL 的孔/位也是实例表一行，内容物 1:1 内联(content_* 组)。
--      一块 96 孔板 = 1 行载体实例 + 96 行孔实例；空孔同样建行，content_type = EMPTY。
--      CARRIER 自身不装物质，content_* 整组为空。
--   3. 树的边 = parent_instance_id + parent_position_code；顶层实例 parent_instance_id 为 NULL。
--   4. 四组绑定:
--        空间 <- zone        slot.zone_code 挂载区域
--        规格 <- wms_container_type  container_type_id / container_type_code（必填）
--        内容 <- wms_content_def     content_def_id / content_def_code（CARRIER、空容器为空）
--        落位 <- wms_slot_info      root_slot_id / root_slot_code（仅顶层实例，内部 WELL 不落 slot）
--   5. 无「启用 / 停用」状态位: 实例的终态是 DISCARDED(报废)，不是停用，故不设 status 列。
--   6. instance_path 走前缀索引(64): 支持 LIKE '/1001/1005/%' 子树查询。
--   7. content_type = EMPTY 表示「能装但现在是空的」；CARRIER 此列为 NULL 表示「不装东西」。
--      两者语义不同，不要互相替代。
--   8. 同级同位唯一: (parent_instance_id, parent_position_code) 由 Service 层校验。
--   9. 不允许删除仍有子节点的实例(否则路径断裂)；嵌套成环由应用层祖先链校验防护。
--  10. instance_status 只回答「这一件现在能不能用」；锁归调度域，本表只保留 RESERVED
--      这一个「被步骤占用」的语义。
--  11. current_vol_ul 与 current_count 二选一填写(液体填体积、固体/计数填数量)。
--  12. 落位只在顶层实例: 一块 96 孔板占 1 个 slot(root_slot_id 有值)，内部 96 孔是子实例、不落 slot。
--      移动整树时只改根实例的 root_slot_id/root_slot_code；「谁占用 slot」按 root_slot_id 反查。
--
-- 已移除的字段(如需恢复，回看 wms.sql v1.4):
--   规格侧:  size_x_mm / size_y_mm / size_z_mm / well_spacing_mm / material /
--            max_nest_depth / temp_min / temp_max / device_labware_name
--   内容物:  require_batch
--   实例侧:  depth / 溯源 source_execution_id/source_node_id / 批次 batch_no/lot_no /
--            复用 reuse_count/last_clean_time / 日期 received_at/opened_at/expired_at
-- 已移除的表(如需恢复，回看 wms.sql v1.4):
--   wms_zone_material_type(区域物料类型约束) / wms_material_info / wms_material_batch /
--   wms_inventory / wms_inventory_history / 出入库与盘点单据 6 组(12 表)
--
-- ext_data 迁移说明(保守迁移，只收描述/坐标/模板类，不动索引与强约束字段):
--   wms_zone_info:       address / rack_rows / rack_cols / slot_capacity
--   wms_slot_info:       row_no / col_no / layer_no
--   wms_material_instance: concentration(实例级覆盖值，标准浓度仍在 content_def)
--   保留为列的原因: temp_min/temp_max(温区沿树继承检索)、biosafety_level(安全校验)、
--   unique_batch(上架硬约束)、device_position_no(在 idx_device 索引)、barcode(扫码检索)、
--   slot_status/occupied_qty/capacity(状态流转与容量校验)
-- =============================================================================

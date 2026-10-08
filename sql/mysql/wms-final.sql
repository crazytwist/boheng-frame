-- =============================================================================
-- Lab-WMS 物料域最终脚本（当前代码对齐版）
-- 版本: v1.5-final    底座: boheng-frame    目标库: MySQL 8.0+ / boheng-frame
--
-- 本文件是物料域唯一应执行的结构脚本，取代：
--   wms.sql                      v1.4 十八表历史脚本，不要再执行
--   wms-instance-minimal.sql     五张表，已并入
--   wms-movement.sql             物料流水，已并入
--   wms-instance-slot-unique.sql 槽位唯一索引，已写进 wms_material_instance
--   wms-menu.sql / wms-menu-movement.sql  菜单，已并入且改为可重复执行
--
-- 不包含：
--   wms-dict.sql                 已过期，字典指向已删除的物料主数据表
--   wms-cleanup-test-data.sql    一次性测试数据清理
--   wms-slot-occupancy-fix.sql   存量数据修复，不是结构
--
-- 六张表：
--   空间  wms_zone_info / wms_slot_info
--   规格  wms_container_type / wms_content_def
--   实物  wms_material_instance（含 uk_root_slot）
--   流水  wms_material_movement
--
-- 与 Java DO 对齐的要点：
--   zone / slot / instance 用 ext_data，不再单列 address、rack_rows、row_no、concentration
--   container_type / content_def 有 image_url
--   槽位占用真相源是 instance.root_slot_id，同一未删除槽位只能有一个实例
--
-- 前置：已导入 ruoyi-vue-pro.sql，且 system_menu 中存在 WMS 目录 id=1348。
-- 警告：第 1 段会 DROP 上述 6 张表后重建，表内数据会清空。
-- 用法：
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 boheng-frame \
--     < sql/mysql/wms-final.sql
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. 重建六张当前表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `wms_material_movement`;
DROP TABLE IF EXISTS `wms_material_instance`;
DROP TABLE IF EXISTS `wms_slot_info`;
DROP TABLE IF EXISTS `wms_zone_info`;
DROP TABLE IF EXISTS `wms_container_type`;
DROP TABLE IF EXISTS `wms_content_def`;

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

-- -----------------------------------------------------------------------------
-- 6. wms_material_movement  物料流水（只增不改）
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 3. 菜单与权限（可重复执行；父目录 WMS 系统 id=1348 须已存在）
-- 执行后清 Redis：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- -----------------------------------------------------------------------------
DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 12732 AND 12761;
DELETE FROM `system_menu` WHERE `id` BETWEEN 12732 AND 12761;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 空间管理（区域 + 槽位）
(12732, '空间管理', '', 2, 7, 1348, 'space', 'ep:grid', 'wms/space/index', 'WmsSpace', 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12733, '区域查询', 'wms:zone:query', 3, 1, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12734, '区域新增', 'wms:zone:create', 3, 2, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12735, '区域修改', 'wms:zone:update', 3, 3, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12736, '区域删除', 'wms:zone:delete', 3, 4, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12737, '槽位查询', 'wms:slot:query', 3, 5, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12738, '槽位新增', 'wms:slot:create', 3, 6, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12739, '槽位修改', 'wms:slot:update', 3, 7, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12740, '槽位删除', 'wms:slot:delete', 3, 8, 12732, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
-- 容器类型
(12741, '容器类型', '', 2, 8, 1348, 'container-type', 'ep:box', 'wms/container-type/index', 'WmsContainerType', 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12742, '容器类型查询', 'wms:container-type:query', 3, 1, 12741, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12743, '容器类型新增', 'wms:container-type:create', 3, 2, 12741, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12744, '容器类型修改', 'wms:container-type:update', 3, 3, 12741, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12745, '容器类型删除', 'wms:container-type:delete', 3, 4, 12741, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
-- 内容物定义
(12746, '内容物定义', '', 2, 9, 1348, 'content-def', 'ep:watermelon', 'wms/content-def/index', 'WmsContentDef', 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12747, '内容物定义查询', 'wms:content-def:query', 3, 1, 12746, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12748, '内容物定义新增', 'wms:content-def:create', 3, 2, 12746, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12749, '内容物定义修改', 'wms:content-def:update', 3, 3, 12746, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12750, '内容物定义删除', 'wms:content-def:delete', 3, 4, 12746, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
-- 物料实例
(12751, '物料实例', '', 2, 10, 1348, 'instance', 'ep:files', 'wms/instance/index', 'WmsInstance', 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12752, '物料实例查询', 'wms:material-instance:query', 3, 1, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12753, '物料实例新增', 'wms:material-instance:create', 3, 2, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12754, '物料实例修改', 'wms:material-instance:update', 3, 3, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(12755, '物料实例删除', 'wms:material-instance:delete', 3, 4, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0');

-- 授权给「普通角色」(role_id = 2)：WMS 目录 + 5 个菜单 + 20 个按钮权限
-- 注：超级管理员(super_admin)无需 role_menu，框架已短路放行
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(2, 12732, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12733, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12734, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12735, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12736, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12737, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12738, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12739, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12740, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12741, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12742, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12743, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12744, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12745, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12746, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12747, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12748, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12749, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12750, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12751, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12752, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12753, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12754, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0'),
(2, 12755, 'admin', '2026-09-26 03:40:00', '', '2026-09-26 03:40:00', b'0');

DELETE FROM `system_role_menu` WHERE `menu_id` IN (12756, 12757, 12758, 12759, 12760, 12761);
DELETE FROM `system_menu` WHERE `id` IN (12756, 12757, 12758, 12759, 12760, 12761);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- 物料流水（独立菜单）
(12756, '物料流水', '', 2, 11, 1348, 'movement', 'ep:tickets', 'wms/movement/index', 'WmsMovement', 0, b'1', b'1', b'1', 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(12757, '流水查询', 'wms:movement:query', 3, 1, 12756, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
-- 物料实例 · 落位操作与消耗（挂在原「物料实例」菜单 id=12751 下）
(12758, '实例上架', 'wms:material-instance:put-in', 3, 5, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(12759, '实例下架', 'wms:material-instance:take-out', 3, 6, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(12760, '实例转移', 'wms:material-instance:transfer', 3, 7, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(12761, '实例消耗', 'wms:material-instance:consume', 3, 8, 12751, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0');

-- 授权给「普通角色」(role_id = 2)
-- 注：超级管理员(super_admin)无需 role_menu，框架已短路放行
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(2, 12756, 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(2, 12757, 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(2, 12758, 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(2, 12759, 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(2, 12760, 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0'),
(2, 12761, 'admin', '2026-09-26 23:00:00', '', '2026-09-26 23:00:00', b'0');

SET FOREIGN_KEY_CHECKS = 1;

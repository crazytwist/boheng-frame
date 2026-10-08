-- =============================================================================
-- 原设计存档：实例域三张表（用户于 2026-09-26 提供）
-- 来源: 用户粘贴的原始 DDL（lab_ 前缀，AUTO_INCREMENT，无 tenant_id）
-- 作用: 实例域的权威设计依据。v1.4 的 wms_container_type / wms_content_def /
--       wms_material_instance 即以此为准重写（表前缀、审计、租户、主键策略按
--       「全面对齐 boheng 底座」的既有决策调整，业务字段与语义保持一致）。
--
-- 三张表的关系（原设计意图，务必保留）:
--   容器类型   管「能装什么」   —— 几何规格 + 层级角色（CARRIER/CONTAINER/WELL）+ 承载的子单元类型
--   内容物定义 管「物质是什么」 —— 浓度 / CAS / 危险等级 / 保质期 / 开封效期 / 供应商货号
--   物料实例   管「这一件实物」 —— 规格绑定 + 内容绑定 + 树层级 + 物理落位
--
-- ★ 关键决策：位置（孔/格位）也是实例（hierarchy_role = WELL），
--   因此内容物 1:1 内联在实例上，不需要独立的「实例内容物」表。
-- =============================================================================


CREATE TABLE `lab_container_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '容器类型唯一编码，如 PLATE_96_WELL / TUBE_EP_15ML / BOTTLE_50ML',
  `type_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '容器类型名称，如 96孔板 / 15ml EP管 / 50ml试剂瓶',
  `container_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '容器大类：PLATE=孔板 TUBE=试管/离心管 BOTTLE=瓶 RACK=托盘/架 VIAL=小瓶 TIP=吸头 CHIP=芯片 FILTER=滤膜 BOX=盒',
  `hierarchy_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '层级角色：CARRIER=载体（承载其他容器，如托盘/孔板） CONTAINER=直接容器（装内容物，如试管/瓶） WELL=孔（孔板的最小单元）',
  `max_vol_ul` decimal(10,2) DEFAULT NULL COMMENT '最大容积（微升），CARRIER 类型为 NULL',
  `well_count` int DEFAULT NULL COMMENT '孔数/位数，仅 CARRIER 类型填写，如 96孔板填 96，24孔试管架填 24',
  `well_rows` int DEFAULT NULL COMMENT '行数，如96孔板=8，24孔架=4，仅 CARRIER 类型',
  `well_cols` int DEFAULT NULL COMMENT '列数，如96孔板=12，24孔架=6，仅 CARRIER 类型',
  `child_type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '子单元类型编码，CARRIER 类型填写其承载的子容器类型，如孔板→WELL_STANDARD，24孔架→TUBE_EP_15ML',
  `size_x_mm` decimal(8,2) DEFAULT NULL COMMENT '外形尺寸 X（mm），机械臂抓取用',
  `size_y_mm` decimal(8,2) DEFAULT NULL COMMENT '外形尺寸 Y（mm）',
  `size_z_mm` decimal(8,2) DEFAULT NULL COMMENT '外形尺寸 Z（mm，高度）',
  `well_spacing_mm` decimal(6,2) DEFAULT NULL COMMENT '孔间距（mm），孔板/试管架的相邻位置间距，移液枪多通道操作用',
  `material` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器材质，如 PP / PC / GLASS / PS',
  `spec_json` json DEFAULT NULL COMMENT '其他规格参数 JSON，如 {"color":"transparent","sterile":true}',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：1=启用 0=停用（停用后不能创建该类型实例）',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明',
  `creator` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `updater` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0=正常 1=已删除',
  `consumable` bit(1) NOT NULL DEFAULT b'0' COMMENT '耗材属性：0-非耗材 1-耗材',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`type_code`) USING BTREE,
  KEY `idx_container_type` (`container_type`,`hierarchy_role`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='容器类型定义，描述各类实验容器的规格和层级结构，归 lab 模块管理';



CREATE TABLE `lab_material_def` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容物唯一编码，如 PH-BUFFER-7 / ETHANOL-75PCT',
  `material_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容物名称，如 pH7标准缓冲液 / 75%乙醇',
  `content_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容物类型：REAGENT=试剂 STANDARD=标准品 BUFFER=缓冲液 SAMPLE=样本 WASTE=废液 MEDIA=培养基 SOLVENT=溶剂',
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计量单位：ml / ul / mg / g / 个',
  `supplier` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `catalog_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商货号/目录号',
  `cas_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'CAS号，化学物质标识，危险品管控用',
  `concentration` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标准浓度描述，如 1mol/L / 75% / pH7.0',
  `storage_temp` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '存储温度要求，如 2~8℃ / -20℃ / 室温(15~25℃)',
  `shelf_life_days` int DEFAULT NULL COMMENT '保质期（天），从入库日期计算，NULL表示不限',
  `open_life_days` int DEFAULT NULL COMMENT '开封后有效期（天），开封后重新计算，NULL表示不限',
  `hazard_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '危险品等级：NONE=无危险 LOW=低危 MEDIUM=中危 HIGH=高危 FLAMMABLE=易燃 TOXIC=有毒',
  `spec_json` json DEFAULT NULL COMMENT '其他规格参数 JSON，如 {"purity":"≥99%","grade":"AR"}',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：1=启用 0=停用',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明',
  `creator` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `updater` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_code` (`material_code`) USING BTREE,
  KEY `idx_content_type` (`content_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='内容物定义，描述试剂/标准品/缓冲液等内容物的属性，归 lab 模块管理';



CREATE TABLE `lab_material_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实例唯一ID，UUID格式，如 INST-20260501-0001',
  `type_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '容器类型编码，关联 lab_container_type.type_code，如 PLATE_96_WELL / TUBE_EP_15ML',
  `barcode` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条形码/二维码，扫码追踪用，同一系统内唯一',
  `parent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父实例ID，关联同表 instance_id。试管在试管架里时填试管架的instance_id，孔在孔板里填孔板的instance_id，顶层容器为NULL',
  `slot_index` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '在父容器中的位置索引，如孔板孔位 A1/B3，试管架位置 01/02，父容器为NULL时此字段也为NULL',
  `slot_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前所在库位ID，关联 lab_slot_info.slot_id。顶层容器才有值，子单元（孔）位置跟随父容器，此字段为NULL',
  `zone_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前所在区域，冗余存储便于按区查询，跟随slot_id所在区域',
  `content_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容物类型：REAGENT/STANDARD/BUFFER/SAMPLE/WASTE/EMPTY。EMPTY表示空容器，CARRIER类型容器此字段为NULL',
  `material_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容物编码，关联 lab_material_def.material_code，EMPTY或CARRIER类型为NULL',
  `batch_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号，试剂溯源用，同一批次的试剂 batch_no 相同',
  `lot_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '厂商批号（Lot Number），与 batch_no 区分：batch_no是内部入库批次，lot_no是厂商原始批号',
  `current_vol_ul` decimal(10,2) DEFAULT NULL COMMENT '当前体积（微升），液体类内容物填写，固体/空容器为NULL，步骤消耗后更新',
  `current_count` int DEFAULT NULL COMMENT '当前数量（个），固体/耗材类填写，液体类为NULL，步骤消耗后更新',
  `concentration` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前浓度描述，覆盖 material_def 的标准浓度（如稀释后填写实际浓度）',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT '实例状态：AVAILABLE=可用 RESERVED=已预留（步骤申请但未取用） IN_USE=使用中 USED=已使用完 EXPIRED=已过期 DISCARDED=已废弃',
  `received_at` date DEFAULT NULL COMMENT '入库日期，保质期从此日期计算',
  `opened_at` date DEFAULT NULL COMMENT '开封日期，开封后有效期从此日期计算，未开封为NULL',
  `expired_at` date DEFAULT NULL COMMENT '过期日期，由入库日期+保质期天数计算，到期自动标记EXPIRED',
  `source_execution_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源流程执行ID，样本类型填写，追踪是哪次实验产生的',
  `source_node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源步骤节点ID，配合 source_execution_id 精确追踪',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明',
  `creator` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人/入库操作人',
  `updater` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `create_time` datetime(3) NOT NULL COMMENT '创建时间（即入库时间）',
  `update_time` datetime(3) NOT NULL COMMENT '最后更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '删除标志：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_id` (`instance_id`) USING BTREE,
  UNIQUE KEY `uk_barcode` (`barcode`) USING BTREE,
  KEY `idx_batch` (`batch_no`) USING BTREE,
  KEY `idx_expired` (`expired_at`,`status`) USING BTREE,
  KEY `idx_material` (`material_code`,`status`) USING BTREE,
  KEY `idx_parent` (`parent_id`) USING BTREE,
  KEY `idx_slot` (`slot_id`) USING BTREE,
  KEY `idx_type_status` (`type_code`,`status`) USING BTREE,
  KEY `idx_zone_status` (`zone_code`,`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1178 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='物料实例，库存中每一个具体的容器实例，含层级关系和位置追踪，归 lab 模块管理';


-- =============================================================================
-- 落到 boheng 底座时的映射（v1.4 已执行）:
--   lab_ 前缀                → wms_
--   id AUTO_INCREMENT        → bigint 雪花（@TableId ASSIGN_ID）
--   instance_id UUID 对外标识 → instance_code（系统按 类型编码+流水号 生成）
--   parent_id (UUID 字符串)   → parent_instance_id (bigint)
--   type_code                → container_type_id + container_type_code
--   material_code            → content_def_id + content_def_code
--   slot_id / slot_index     → root_slot_id(+code) / parent_position_code
--   status                   → instance_status（六态不变）
--   enabled 1=启用            → status 0=开启 / 1=停用（CommonStatusEnum）
--   UNIQUE KEY               → 普通索引 + Service 层唯一性校验
--   datetime(3) / varchar(128) NULL → datetime 带默认 / varchar(64) DEFAULT ''
--   无 tenant_id             → 全表补 tenant_id
--   zone_code 冗余            → 不冗余（区域属槽位，整盒移动会变）
--   新增（v1.4 增强，原设计没有）: instance_path 物化路径 + depth + root_instance_id 整树冗余、
--                                 device_labware_name 设备板型映射、position_naming 位置命名规则
-- =============================================================================

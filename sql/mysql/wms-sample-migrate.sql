-- =============================================================================
-- WMS 样本域增量迁移（MySQL 8.0+，存量库幂等，可重复执行）
-- 底座: boheng-frame (cn.boheng.frame)    目标库: boheng-frame
--
-- 背景:
--   样本是「业务对象」，独立于实物（wms_material_instance）。样本可提前登记，
--   实例化时把 sample_id 挂到 material_instance 上（参考 labillion 的样本处理）。
--   三层模型:
--     wms_content_def        物质定义（样本「是什么物质」）
--     wms_sample_info        样本主表（谱系/状态/实验参数/流程关联）—— 本脚本新增
--     wms_material_instance  实物实例（加 sample_id 列挂载样本）
--
-- 本脚本内容:
--   1. wms_material_instance 加 sample_id 列 + 索引（幂等）
--   2. wms_sample_info 建表（IF NOT EXISTS）
--   3. 样本类型字典 wms_sample_type（system_dict_type + system_dict_data）
--   4. 样本管理菜单 + 按钮权限（挂在 WMS 目录 id=1348 下）
--
-- 执行后清 Redis：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. wms_material_instance 加 sample_id 列（幂等：列存在则跳过）
-- -----------------------------------------------------------------------------
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wms_material_instance'
      AND COLUMN_NAME = 'sample_id'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `wms_material_instance` ADD COLUMN `sample_id` varchar(128) NULL DEFAULT NULL COMMENT ''样本业务编号(关联 wms_sample_info.sample_id，样本类实例填写，试剂/空容器为 NULL)'' AFTER `content_type`, ADD INDEX `idx_sample_id`(`sample_id` ASC) USING BTREE',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. wms_sample_info  样本主表（样本业务对象：谱系/状态/实验参数/流程关联）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wms_sample_info` (
  `id` bigint NOT NULL COMMENT '主键',
  `sample_id` varchar(128) NOT NULL COMMENT '样本业务编号(条码/二维码，租户内唯一)',
  `parent_sample_id` varchar(128) NULL DEFAULT NULL COMMENT '父样本编号(NULL=原始样本)',
  `root_sample_id` varchar(128) NULL DEFAULT NULL COMMENT '根样本编号(原始样本=自身，查整棵谱系树)',
  `generation` int NOT NULL DEFAULT 0 COMMENT '谱系代数(原始=0，每拆分/合并一次+1)',
  `derive_type` varchar(32) NOT NULL DEFAULT 'ORIGINAL' COMMENT '衍生类型: ORIGINAL 原始/SPLIT 拆分/MERGE 合并/ALIQUOT 分装/ADD_REAGENT 加试剂',
  `sample_type` varchar(64) NULL DEFAULT NULL COMMENT '样本类型(字典 wms_sample_type): WATER 水样/BLOOD 血样/SOIL 土样/...',
  `sample_name` varchar(128) NULL DEFAULT NULL COMMENT '样本名称/描述',
  `content_def_id` bigint NULL DEFAULT NULL COMMENT '内容物定义编号(样本是什么物质，关联 wms_content_def)',
  `content_def_code` varchar(64) NULL DEFAULT NULL COMMENT '内容物编码(冗余，编码寻址)',
  `container_type_code` varchar(64) NULL DEFAULT NULL COMMENT '期望容器类型编码(登记时可指定，关联 wms_container_type)',
  `initial_vol_ul` decimal(10,2) NULL DEFAULT NULL COMMENT '初始体积(μL，登记承诺量，不再变更；当前量在实例 current_vol_ul)',
  `experiment_params` json NULL DEFAULT NULL COMMENT '实验参数(JSON，流程步骤执行时读，如 {"targetPh":7.2,"volumeUl":500})',
  `ext_data` json NULL DEFAULT NULL COMMENT '扩展属性(JSON，不同类型样本不同属性，如 {"ph":7.2,"color":"透明"})',
  `status` varchar(32) NOT NULL DEFAULT 'REGISTERED' COMMENT '样本状态: REGISTERED 已登记/LOADED 已装载/IN_PROCESS 处理中/SPLIT 已拆分/COMPLETED 已完成/ARCHIVED 已归档/REJECTED 不合格/LOST 丢失',
  `current_execution_id` varchar(64) NULL DEFAULT NULL COMMENT '当前流程执行编号(样本在哪个流程)',
  `current_node_id` varchar(64) NULL DEFAULT NULL COMMENT '当前所在步骤节点编号',
  `batch_no` varchar(64) NULL DEFAULT NULL COMMENT '样本批次号(同一批送检样本共享；区别于实例的入库批次)',
  `order_no` varchar(64) NULL DEFAULT NULL COMMENT '检测单/委托单号',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级: 0 普通/1 加急/2 特急',
  `source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL 手动/IMPORT 导入/LIS 系统对接/DEVICE 设备采集',
  `external_id` varchar(64) NULL DEFAULT NULL COMMENT '外部系统ID(LIS单号等)',
  `collected_at` datetime NULL DEFAULT NULL COMMENT '采样时间',
  `received_at` datetime NULL DEFAULT NULL COMMENT '收样时间(到达实验室)',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '样本有效期',
  `description` varchar(512) NULL DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `sample_id_key` varchar(128) GENERATED ALWAYS AS (if((`deleted` = b'0'),`sample_id`,NULL)) VIRTUAL COMMENT '样本编号唯一占位键(生成列)：未删除时=sample_id，否则 NULL，用于唯一约束',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sample_id`(`tenant_id` ASC, `sample_id_key` ASC) USING BTREE,
  INDEX `idx_parent_sample`(`parent_sample_id` ASC) USING BTREE,
  INDEX `idx_root_sample`(`root_sample_id` ASC) USING BTREE,
  INDEX `idx_sample_type`(`sample_type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_current_execution`(`current_execution_id` ASC) USING BTREE,
  INDEX `idx_content_def_id`(`content_def_id` ASC) USING BTREE,
  INDEX `idx_batch_no`(`batch_no` ASC) USING BTREE,
  INDEX `idx_order_no`(`order_no` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '样本主表(样本业务对象：谱系/状态/实验参数/流程关联；实物经 wms_material_instance.sample_id 关联)';
-- 说明:
--   1. 样本与实物分离: 样本是业务对象(可提前登记、无实物)，实物是 material_instance。
--      一份样本可有 0 个(未实例化)或多个(分装多管)实物实例，通过 sample_id 单向关联。
--   2. 位置不落样本表: 样本位置真相源 = 其实例的 root_slot_id(未实例化则无位置)。
--   3. 体积只留 initial_vol_ul(登记承诺量)，当前体积在实例 current_vol_ul，避免双真相源。
--   4. 实验参数挂样本(experiment_params)，流程步骤执行时从样本读取，不挂实物。
--   5. 样本状态(status 八态)与实例状态(instance_status 六态)两套正交，各管各的。

-- -----------------------------------------------------------------------------
-- 3. 样本类型字典 wms_sample_type（幂等：存在则跳过插入）
-- -----------------------------------------------------------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`,
                                `updater`, `update_time`, `deleted`)
SELECT '样本类型', 'wms_sample_type', 0, '样本细分类型值域（wms_sample_info.sample_type 引用）',
       'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'wms_sample_type' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`,
                                `css_class`, `remark`, `creator`, `create_time`,
                                `updater`, `update_time`, `deleted`)
SELECT t.sort, t.label, t.value, 'wms_sample_type', 0, t.color_type, '', t.remark,
       'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT 1 AS sort, '水样' AS label, 'WATER' AS value, 'primary' AS color_type, '地表水/污水/饮用水等水质样本' AS remark
    UNION ALL SELECT 2, '血样', 'BLOOD', 'danger', '血液样本'
    UNION ALL SELECT 3, '尿样', 'URINE', 'warning', '尿液样本'
    UNION ALL SELECT 4, '组织', 'TISSUE', 'warning', '组织切片/匀浆'
    UNION ALL SELECT 5, '土样', 'SOIL', 'info', '土壤/沉积物样本'
    UNION ALL SELECT 6, '气样', 'AIR', 'info', '气体/大气样本'
    UNION ALL SELECT 7, '食品', 'FOOD', 'success', '食品/农产品样本'
    UNION ALL SELECT 8, '混合物', 'MIXTURE', 'info', '混合/复合样本'
    UNION ALL SELECT 9, '其他', 'OTHER', 'info', '其他类型样本'
) t
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'wms_sample_type' AND `value` = t.value AND `deleted` = b'0');

-- -----------------------------------------------------------------------------
-- 4. 样本管理菜单 + 按钮权限（幂等；父目录 WMS 物料空间 id=1348）
--    ⚠️ ID 用空闲段 12834-12838（勿用 12762-12766，那段是设备域「设备管理」目录 + 设备台账）
-- -----------------------------------------------------------------------------
DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 12834 AND 12838;
DELETE FROM `system_menu` WHERE `id` BETWEEN 12834 AND 12838;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12834, '样本管理', '', 2, 12, 1348, 'sample', 'ep:collection-tag', 'wms/sample/index', 'WmsSample', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12835, '样本查询', 'wms:sample:query', 3, 1, 12834, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12836, '样本新增', 'wms:sample:create', 3, 2, 12834, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12837, '样本修改', 'wms:sample:update', 3, 3, 12834, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12838, '样本删除', 'wms:sample:delete', 3, 4, 12834, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(2, 12834, 'admin', NOW(), '', NOW(), b'0'),
(2, 12835, 'admin', NOW(), '', NOW(), b'0'),
(2, 12836, 'admin', NOW(), '', NOW(), b'0'),
(2, 12837, 'admin', NOW(), '', NOW(), b'0'),
(2, 12838, 'admin', NOW(), '', NOW(), b'0');

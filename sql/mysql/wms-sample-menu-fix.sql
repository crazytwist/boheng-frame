-- -----------------------------------------------------------------------------
-- 修复：样本菜单 ID 撞车设备域（wms-sample-migrate.sql 误用 12762-12766）
--
-- 根因：12762-12766 是设备域「设备管理」目录(id=12762) + 「设备台账」菜单(12763)
--       + 4 个按钮(12764-12767) 的 ID；样本迁移脚本复用了这段 ID，把设备管理
--       目录覆盖成「样本管理」，导致设备域子菜单（设备动作/属性/参数集/解析规则/
--       命令记录/测量记录/仿真配置，parent_id=12762）全部挂到样本管理下面。
--
-- 修复动作（幂等）：
--   1. 删除误写入的样本菜单 12762-12766 及其授权
--   2. 恢复设备域 12762-12766（设备管理 + 设备台账 + 3 按钮；12767 未受损保留）
--   3. 样本菜单改用空闲段 12834-12838
--
-- 执行后需清 Redis 权限缓存：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- -----------------------------------------------------------------------------

-- 1. 清掉误写入的样本菜单（12762-12766）及其授权
DELETE FROM `system_role_menu` WHERE `menu_id` IN (12762, 12763, 12764, 12765, 12766);
DELETE FROM `system_menu` WHERE `id` IN (12762, 12763, 12764, 12765, 12766);

-- 2. 恢复设备域 12762-12766（设备管理目录 + 设备台账 + 4 按钮）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12762, '设备管理', '', 1, 320, 0, '/device', 'ep:monitor', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12763, '设备台账', '', 2, 1, 12762, 'device', 'ep:cpu', 'device/index', 'WmsDeviceInfo', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12764, '设备查询', 'device:info:query', 3, 1, 12763, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12765, '设备新增', 'device:info:create', 3, 2, 12763, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12766, '设备修改', 'device:info:update', 3, 3, 12763, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(2, 12762, 'admin', NOW(), '', NOW(), b'0'),
(2, 12763, 'admin', NOW(), '', NOW(), b'0'),
(2, 12764, 'admin', NOW(), '', NOW(), b'0'),
(2, 12765, 'admin', NOW(), '', NOW(), b'0'),
(2, 12766, 'admin', NOW(), '', NOW(), b'0');

-- 3. 样本菜单改用空闲段 12834-12838（挂在 WMS 物料空间 id=1348 下）
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

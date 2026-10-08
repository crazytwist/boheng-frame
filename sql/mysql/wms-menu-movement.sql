-- ----------------------------------------------------------------------------
-- WMS 物料流水 + 实例落位操作 权限数据（增量）
--
-- 新增内容：
--   1. 独立菜单「物料流水」(id=12756)  + 查询按钮 wms:movement:query
--   2. 物料实例(id=12751) 下新增 4 个操作按钮：
--        上架 wms:material-instance:put-in
--        下架 wms:material-instance:take-out
--        转移 wms:material-instance:transfer
--        消耗 wms:material-instance:consume
--
-- 父目录：WMS 系统(id=1348)
-- 脚本幂等：可重复执行（先按固定 id 清理再插入）
-- 执行后需清 Redis 权限缓存：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- ----------------------------------------------------------------------------

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

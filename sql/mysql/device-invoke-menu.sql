-- 设备台账：调用设备动作按钮权限。原来复用 device:info:update，拆出来单独授权
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 12797, '设备调用', 'device:info:invoke', 3, 5, 12763, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 12797);

-- 原来有设备修改权限的角色，同时授予设备调用
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT rm.role_id, 12797, 'admin', NOW(), 'admin', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
WHERE rm.menu_id = 12766 AND rm.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 12797 AND x.deleted = 0);

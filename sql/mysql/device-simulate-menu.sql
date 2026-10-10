-- ----------------------------------------------------------------------------
-- 设备域 仿真配置 菜单与权限
--
-- 新增内容（挂在「设备管理」目录 12762 下）：
--   菜单「仿真配置」(id=12792) + 4 个按钮：
--        device:simulate:query|create|update|delete
--
-- ⚠️ component_name 必须与 device/simulate/index.vue 的 defineOptions({ name }) 一致：DeviceSimulate
-- 授权范围与「命令记录」(12788) 相同：哪些角色、哪些租户能看命令记录，就同样授予仿真配置。
--
-- 脚本幂等：可重复执行（先按固定 id 清理再插入）
-- 执行后需清 Redis 权限缓存：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- ----------------------------------------------------------------------------

DELETE FROM `system_role_menu` WHERE `menu_id` IN (12792, 12793, 12794, 12795, 12796);
DELETE FROM `system_menu` WHERE `id` IN (12792, 12793, 12794, 12795, 12796);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12792, '仿真配置', '', 2, 8, 12762, 'simulate', 'ep:magic-stick', 'device/simulate/index', 'DeviceSimulate', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12793, '仿真查询', 'device:simulate:query', 3, 1, 12792, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12794, '仿真新增', 'device:simulate:create', 3, 2, 12792, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12795, '仿真修改', 'device:simulate:update', 3, 3, 12792, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12796, '仿真删除', 'device:simulate:delete', 3, 4, 12792, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, 'admin', NOW(), '', NOW(), b'0', rm.`tenant_id`
FROM (SELECT DISTINCT `role_id`, `tenant_id` FROM `system_role_menu` WHERE `menu_id` = 12788 AND `deleted` = b'0') rm
CROSS JOIN `system_menu` m
WHERE m.`id` BETWEEN 12792 AND 12796;

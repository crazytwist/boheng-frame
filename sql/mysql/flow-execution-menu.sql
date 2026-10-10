-- ----------------------------------------------------------------------------
-- 实验流程 · 运行管理 菜单与权限
--
--   菜单「流程运行」(id=12827) + 按钮 flow:execution:*
--     查询   flow:execution:query   列表 / 详情 / 时间线
--     启动   flow:execution:create  启动运行
--     取消   flow:execution:update  取消运行
--     人工   flow:execution:signal  人工确认 / 拒绝
--
-- component_name 必须与页面 defineOptions({ name }) 一致。
-- 脚本幂等。执行后清 Redis：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- 授权策略与 flow-menu.sql 相同：跟着能看设备台账的角色走，兜底 role_id=2、租户 1。
-- 超级管理员不需要这张表。
-- ----------------------------------------------------------------------------

DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 12827 AND 12831;
DELETE FROM `system_menu` WHERE `id` BETWEEN 12827 AND 12831;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12827, '流程运行', '', 2, 4, 12810, 'execution', 'ep:video-play', 'flow/execution/index', 'FlowExecution', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12828, '运行查询', 'flow:execution:query', 3, 1, 12827, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12829, '运行启动', 'flow:execution:create', 3, 2, 12827, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12830, '运行取消', 'flow:execution:update', 3, 3, 12827, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12831, '人工确认', 'flow:execution:signal', 3, 4, 12827, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, 'admin', NOW(), '', NOW(), b'0', rm.`tenant_id`
FROM (
    SELECT DISTINCT `role_id`, `tenant_id` FROM `system_role_menu` WHERE `menu_id` = 12810 AND `deleted` = b'0'
    UNION
    SELECT 2, 1
) rm
CROSS JOIN `system_menu` m
WHERE m.`id` BETWEEN 12827 AND 12831;

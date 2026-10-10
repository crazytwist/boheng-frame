-- ----------------------------------------------------------------------------
-- 实验流程 · 流程预览 菜单与权限（只读 DAG 视图，不影响原「流程步骤」表格编排）
--
--   菜单「流程预览」(id=12832) + 按钮 flow:preview:query
--
-- component_name 必须与页面 defineOptions({ name }) 一致（FlowPreview）。
-- 脚本幂等。执行后清 Redis：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- 授权策略与 flow-menu.sql 相同：跟着能看「实验流程」目录的角色走，兜底 role_id=2、租户 1。
-- 超级管理员不需要这张表。
-- ----------------------------------------------------------------------------

DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 12832 AND 12833;
DELETE FROM `system_menu` WHERE `id` BETWEEN 12832 AND 12833;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12832, '流程预览', '', 2, 5, 12810, 'preview', 'ep:view', 'flow/preview/index', 'FlowPreview', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12833, '预览查询', 'flow:preview:query', 3, 1, 12832, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, 'admin', NOW(), '', NOW(), b'0', rm.`tenant_id`
FROM (
    SELECT DISTINCT `role_id`, `tenant_id` FROM `system_role_menu` WHERE `menu_id` = 12810 AND `deleted` = b'0'
    UNION
    SELECT 2, 1
) rm
CROSS JOIN `system_menu` m
WHERE m.`id` BETWEEN 12832 AND 12833;

-- ----------------------------------------------------------------------------
-- 实验流程 · 流程编排（拖拽画布）菜单与权限
--
--   菜单「流程编排」(id=12839) 指向 flow/designer/index（LogicFlow 画布），
--   复用 flow:relation:* 按钮权限（增删改查节点），不单独新增按钮段。
--
-- component_name 必须与页面 defineOptions({ name }) 一致（FlowDesigner）。
-- 脚本幂等。执行后清 Redis：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- 授权策略与 flow-preview-menu.sql 相同：跟着能看「实验流程」目录的角色走，兜底 role_id=2、租户 1。
-- 超级管理员不需要这张表。
--
-- 注意：本菜单依赖画布页 web/src/views/flow/designer/index.vue 已落地后再执行。
-- ----------------------------------------------------------------------------

DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 12839 AND 12840;
DELETE FROM `system_menu` WHERE `id` BETWEEN 12839 AND 12840;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12839, '流程编排', '', 2, 4, 12810, 'designer', 'ep:connection', 'flow/designer/index', 'FlowDesigner', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, 'admin', NOW(), '', NOW(), b'0', rm.`tenant_id`
FROM (
    SELECT DISTINCT `role_id`, `tenant_id` FROM `system_role_menu` WHERE `menu_id` = 12810 AND `deleted` = b'0'
    UNION
    SELECT 2, 1
) rm
CROSS JOIN `system_menu` m
WHERE m.`id` BETWEEN 12839 AND 12840;

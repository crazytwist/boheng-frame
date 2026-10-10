-- ----------------------------------------------------------------------------
-- 实验流程 菜单与权限
--
--   目录「实验流程」(id=12810)
--   菜单「流程定义」(id=12811) + 按钮 flow:definition:*
--   菜单「步骤定义」(id=12817) + 按钮 flow:step:*
--   菜单「流程步骤」(id=12822) + 按钮 flow:relation:*
--
-- component_name 必须与页面 defineOptions({ name }) 一致。
-- component 不能写 flow/index：前端按子串找页面，会先命中 ai/workflow/index.vue。
-- 步骤、流程、关联各是一张列表。关联只把已有步骤挂到草稿上。
--
-- 脚本幂等。执行后清 Redis：permission_menu_ids:* / menu_role_ids:* / user_role_ids:*
-- 授权给能看设备台账的角色；若还没有，则给普通角色 role_id=2、租户 1。
-- 超级管理员不需要这张表。
-- ----------------------------------------------------------------------------

DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 12810 AND 12826;
DELETE FROM `system_menu` WHERE `id` BETWEEN 12810 AND 12826;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12810, '实验流程', '', 1, 330, 0, '/flow', 'ep:connection', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12811, '流程定义', '', 2, 1, 12810, 'definition', 'ep:operation', 'flow/definition/index', 'FlowDefinition', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12812, '流程查询', 'flow:definition:query', 3, 1, 12811, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12813, '流程新增', 'flow:definition:create', 3, 2, 12811, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12814, '流程修改', 'flow:definition:update', 3, 3, 12811, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12815, '流程删除', 'flow:definition:delete', 3, 4, 12811, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12816, '流程发布', 'flow:definition:publish', 3, 5, 12811, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12817, '步骤定义', '', 2, 2, 12810, 'step', 'ep:cpu', 'flow/step/index', 'FlowStep', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12818, '步骤查询', 'flow:step:query', 3, 1, 12817, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12819, '步骤新增', 'flow:step:create', 3, 2, 12817, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12820, '步骤修改', 'flow:step:update', 3, 3, 12817, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12821, '步骤删除', 'flow:step:delete', 3, 4, 12817, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12822, '流程步骤', '', 2, 3, 12810, 'relation', 'ep:link', 'flow/relation/index', 'FlowRelation', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12823, '关联查询', 'flow:relation:query', 3, 1, 12822, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12824, '关联新增', 'flow:relation:create', 3, 2, 12822, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12825, '关联修改', 'flow:relation:update', 3, 3, 12822, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12826, '关联删除', 'flow:relation:delete', 3, 4, 12822, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, 'admin', NOW(), '', NOW(), b'0', rm.`tenant_id`
FROM (
    SELECT DISTINCT `role_id`, `tenant_id` FROM `system_role_menu` WHERE `menu_id` = 12763 AND `deleted` = b'0'
    UNION
    SELECT 2, 1
) rm
CROSS JOIN `system_menu` m
WHERE m.`id` BETWEEN 12810 AND 12826;

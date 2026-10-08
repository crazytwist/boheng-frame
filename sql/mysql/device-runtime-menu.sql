-- 解析规则 / 命令记录 / 测量记录，以及动作上的解析规则编码
ALTER TABLE `device_action`
    ADD COLUMN `codec_code` varchar(64) NULL COMMENT '响应解析规则编码' AFTER `request_template`;

DELETE FROM `system_role_menu` WHERE `menu_id` IN (12783, 12784, 12785, 12786, 12787, 12788, 12789, 12790, 12791);
DELETE FROM `system_menu` WHERE `id` IN (12783, 12784, 12785, 12786, 12787, 12788, 12789, 12790, 12791);

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(12783, '解析规则', '', 2, 5, 12762, 'codec', 'ep:document', 'device/codec/index', 'DeviceCodec', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12784, '解析规则查询', 'device:codec:query', 3, 1, 12783, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12785, '解析规则新增', 'device:codec:create', 3, 2, 12783, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12786, '解析规则修改', 'device:codec:update', 3, 3, 12783, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12787, '解析规则删除', 'device:codec:delete', 3, 4, 12783, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12788, '命令记录', '', 2, 6, 12762, 'command', 'ep:list', 'device/command/index', 'DeviceCommand', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12789, '命令查询', 'device:command:query', 3, 1, 12788, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12790, '测量记录', '', 2, 7, 12762, 'measurement', 'ep:data-line', 'device/measurement/index', 'DeviceMeasurement', 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0'),
(12791, '测量查询', 'device:measurement:query', 3, 1, 12790, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), '', NOW(), b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 2, id, 'admin', NOW(), '', NOW(), b'0', 0 FROM `system_menu` WHERE `id` BETWEEN 12783 AND 12791;

-- =============================================================================
-- 命令记录：新增「手动结束」按钮权限
--
-- 背景：回调方式（result_mode=CALLBACK）的命令等不到 7.1.3 时会一直停在 WAITING，
-- 直到 Sweeper 按截止时刻判超时。回调丢失是常态，运维需要能据现场事实直接给结论。
--
-- 落位：设备域菜单 12762-12797 已占，12810 起是流程域，故取 12798。
-- 父菜单 12788（命令记录），与 12789（命令查询）同级按钮。
--
-- 特性：幂等。同一权限已存在则不插。
-- 执行：
--   docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 \
--     -D boheng-frame < sql/mysql/device-command-close-migrate.sql
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
 `component_name`, `status`, `visible`, `always_show`, `keep_alive`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`)
SELECT 12798, '命令手动结束', 'device:command:close', 3, 2, 12788, '', '', NULL,
       NULL, 0, b'1', b'1', b'1', 'admin', NOW(),
       '', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT `id` FROM `system_menu`
                   WHERE `permission` = 'device:command:close' AND `deleted` = b'0') AS existing
);

-- 校验：命令记录下应有两个按钮
SELECT `id`, `parent_id`, `name`, `permission`, `type`, `sort`
FROM `system_menu`
WHERE `parent_id` = 12788 AND `deleted` = b'0'
ORDER BY `sort`;

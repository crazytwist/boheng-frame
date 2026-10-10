-- 把已经建好的旧 flow_node 拆成「步骤表 + 关联表」。
-- 新库只执行 flow-definition.sql，不要再跑本脚本。
-- 可重复执行：步骤表已存在则跳过建表；列已拆完则跳过搬运。

SET NAMES utf8mb4;
SET @db = DATABASE();

CREATE TABLE IF NOT EXISTS `flow_step` (
    `id`                    bigint       NOT NULL COMMENT '主键(雪花)',
    `step_key`              varchar(32)  NOT NULL COMMENT '步骤编码。创建后不可改，如 spin、read',
    `step_key_guard`        varchar(32)  GENERATED ALWAYS AS (IF(`deleted` = b'0', `step_key`, NULL)) VIRTUAL COMMENT '生成列。逻辑删除后释放步骤编码',
    `name`                  varchar(128) NOT NULL COMMENT '步骤名称',
    `device_code`           varchar(64)  NOT NULL COMMENT '台账上的设备编码',
    `action_code`           varchar(64)  NOT NULL COMMENT '该设备类型下的动作编码',
    `timeout_ms`            bigint       NOT NULL COMMENT '这一步最多等待的毫秒数',
    `max_attempts`          int          NOT NULL COMMENT '含首次。当前运行时尚未执行',
    `remark`                varchar(512) NULL COMMENT '说明',
    `status`                tinyint      NOT NULL DEFAULT 0 COMMENT '0 启用，可以被流程引用；1 停用',
    `creator`               varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_flow_step` (`tenant_id`, `step_key_guard`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '实验步骤。可被多条流程引用';

DROP PROCEDURE IF EXISTS flow_split_step;
DELIMITER $$
CREATE PROCEDURE flow_split_step()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'flow_node' AND COLUMN_NAME = 'type_config') > 0 THEN

        IF (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'flow_node' AND COLUMN_NAME = 'step_id') = 0 THEN
            ALTER TABLE `flow_node`
                ADD COLUMN `step_id` bigint NULL COMMENT '关联的步骤。设备和动作在步骤表上' AFTER `definition_id`;
        END IF;

        INSERT INTO `flow_step` (`id`, `step_key`, `name`, `device_code`, `action_code`, `timeout_ms`, `max_attempts`,
                                 `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
        SELECT n.`id`,
               CONCAT(LEFT(n.`node_id`, 10), '_', RIGHT(CAST(n.`id` AS CHAR), 21)),
               n.`name`,
               IFNULL(JSON_UNQUOTE(JSON_EXTRACT(n.`type_config`, '$.deviceCode')), ''),
               IFNULL(JSON_UNQUOTE(JSON_EXTRACT(n.`type_config`, '$.actionCode')), ''),
               n.`timeout_ms`,
               n.`max_attempts`,
               0,
               n.`creator`, n.`create_time`, n.`updater`, n.`update_time`, b'0', n.`tenant_id`
        FROM `flow_node` n
        WHERE n.`deleted` = b'0'
          AND n.`step_id` IS NULL
          AND NOT EXISTS (SELECT 1 FROM `flow_step` s WHERE s.`id` = n.`id`);

        UPDATE `flow_node` SET `step_id` = `id` WHERE `step_id` IS NULL AND `deleted` = b'0';

        ALTER TABLE `flow_node` DROP COLUMN `fail_strategy`;
        ALTER TABLE `flow_node` DROP COLUMN `max_attempts`;
        ALTER TABLE `flow_node` DROP COLUMN `timeout_ms`;
        ALTER TABLE `flow_node` DROP COLUMN `type_config`;
        ALTER TABLE `flow_node` DROP COLUMN `step_type`;
        ALTER TABLE `flow_node` DROP COLUMN `name`;
    END IF;

    IF (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'flow_node' AND INDEX_NAME = 'idx_flow_node_step') = 0
       AND (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'flow_node' AND COLUMN_NAME = 'step_id') > 0 THEN
        ALTER TABLE `flow_node` ADD KEY `idx_flow_node_step` (`step_id`);
    END IF;
END$$
DELIMITER ;

CALL flow_split_step();
DROP PROCEDURE IF EXISTS flow_split_step;

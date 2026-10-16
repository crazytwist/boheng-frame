-- 设备台账增加登录配置。调用 HTTP 动作前先登录，token 缓存在进程内，失效后重新登录。
-- 可重复执行：列已存在时跳过。

SET NAMES utf8mb4;

SET @db = DATABASE();

DROP PROCEDURE IF EXISTS device_add_login_column;
DELIMITER $$
CREATE PROCEDURE device_add_login_column(IN col_name varchar(64), IN ddl text)
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'device_info' AND COLUMN_NAME = col_name) = 0 THEN
        SET @stmt = ddl;
        PREPARE s FROM @stmt;
        EXECUTE s;
        DEALLOCATE PREPARE s;
    END IF;
END$$
DELIMITER ;

CALL device_add_login_column('login_path', 'ALTER TABLE `device_info` ADD COLUMN `login_path` varchar(255) NULL COMMENT ''登录相对路径'' AFTER `endpoint_url`');
CALL device_add_login_column('login_method', 'ALTER TABLE `device_info` ADD COLUMN `login_method` varchar(16) NULL COMMENT ''登录 HTTP 方法'' AFTER `login_path`');
CALL device_add_login_column('login_username', 'ALTER TABLE `device_info` ADD COLUMN `login_username` varchar(128) NULL COMMENT ''登录用户名'' AFTER `login_method`');
CALL device_add_login_column('login_password', 'ALTER TABLE `device_info` ADD COLUMN `login_password` varchar(255) NULL COMMENT ''登录密码'' AFTER `login_username`');
CALL device_add_login_column('login_username_key', 'ALTER TABLE `device_info` ADD COLUMN `login_username_key` varchar(64) NULL COMMENT ''用户名字段名'' AFTER `login_password`');
CALL device_add_login_column('login_password_key', 'ALTER TABLE `device_info` ADD COLUMN `login_password_key` varchar(64) NULL COMMENT ''密码字段名'' AFTER `login_username_key`');
CALL device_add_login_column('token_path', 'ALTER TABLE `device_info` ADD COLUMN `token_path` varchar(128) NULL COMMENT ''token JSON 路径'' AFTER `login_password_key`');
CALL device_add_login_column('token_header', 'ALTER TABLE `device_info` ADD COLUMN `token_header` varchar(64) NULL COMMENT ''token 请求头'' AFTER `token_path`');
CALL device_add_login_column('token_prefix', 'ALTER TABLE `device_info` ADD COLUMN `token_prefix` varchar(32) NULL COMMENT ''token 前缀'' AFTER `token_header`');
CALL device_add_login_column('token_ttl_sec', 'ALTER TABLE `device_info` ADD COLUMN `token_ttl_sec` int NULL COMMENT ''token 缓存秒数'' AFTER `token_prefix`');

DROP PROCEDURE IF EXISTS device_add_login_column;

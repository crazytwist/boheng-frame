-- 设备动作：请求报文格式
ALTER TABLE `device_action`
    ADD COLUMN `body_format` varchar(16) NULL COMMENT '请求报文格式: JSON/FORM 表单/TEXT 纯文本/XML' AFTER `http_method`;

-- =============================================================================
-- 设备指令回调：设备上配回调地址和默认等待时间，动作上选是否等回调
-- 已有库执行一次即可；全新库直接用 device.sql
-- =============================================================================

ALTER TABLE `device_info`
    ADD COLUMN `callback_url`         varchar(512) NULL COMMENT '设备回调平台用的地址。空则用平台默认地址；可写 ${commandNo}、${callbackToken}、${deviceCode} 占位符' AFTER `callback_enabled`,
    ADD COLUMN `callback_timeout_sec` int          NULL COMMENT '默认等回调的秒数(1-600)。动作上没填时用它，都没填按 300' AFTER `callback_url`;

ALTER TABLE `device_action`
    ADD COLUMN `result_mode`          varchar(16)  NOT NULL DEFAULT 'SYNC_RETURN' COMMENT '结果怎么拿: SYNC_RETURN 直接看响应/CALLBACK 等设备回调' AFTER `poll_max_times`,
    ADD COLUMN `callback_timeout_sec` int          NULL COMMENT '等回调的秒数(1-600)；为空用设备台账上的默认值' AFTER `result_mode`;

ALTER TABLE `device_simulate_profile`
    MODIFY COLUMN `return_mode`   varchar(16) NOT NULL DEFAULT 'SYNC' COMMENT '回收方式: SYNC 同步返回/CALLBACK 受理后由仿真设备回调/POLL 受理后由服务端轮询',
    MODIFY COLUMN `async_wait_ms` int         NOT NULL DEFAULT 0 COMMENT '等待毫秒。CALLBACK=受理后多久回调，最大 600000；POLL=结果就绪前的时长，最大 120000';

UPDATE `device_simulate_profile` SET `return_mode` = 'CALLBACK' WHERE `return_mode` = 'ASYNC';

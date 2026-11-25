-- ============================================================================
-- AGV 生成任务单（genAgvSchedulingTask）输入契约 + 报文模板
--
-- 报文体按厂商确认的 4 个字段：
--   agvCode  = 1900              固定（车号，写死在模板里）
--   reqCode  = ${randomHex(16)}  每次随机，16 位大写十六进制
--   taskTyp  = ${taskTyp}        业务输入，由流程/调用方传
--   wbCode   = 006800BB031000    固定（目标工位）
--
-- 约定说明：
--   · 只有 taskTyp 进输入契约。固定值属于协议知识，不进契约，
--     否则流程侧会被迫传一堆无意义的值；流水号由内置函数生成，同样不进契约。
--   · reqCode 调用方硬传会被 DeviceParamResolver.rejectUnknownParams 拒掉，
--     这是有意的：保证同一个流水号不会被两条任务复用。
--   · agvCode 写死意味着这个动作只服务 1900 这台车。将来如果新增第二台 AGV，
--     把它改成 ${device.serialNo} 并在 device_info.serial_no 上填车号即可，
--     模板不用再复制一份。
--
-- 历史：更早的契约是一坨「示例值」式的旧写法（"podCode":"100001" 这种标量简写），
--       只有 11 个字段、缺 clientCode 等 6 个，调用时会被 rejectUnknownParams 拒掉。
--       那次重建把 15 个字段全塞进了契约，属于「把 schema 当成报文镜像」的过度配置，
--       本次按「只声明业务输入」的原则收敛回 1 个。
--
-- 幂等：纯 UPDATE，可重复执行。
-- ============================================================================

SET @schema := '{
  "taskTyp": {"label": "任务类型", "type": "STRING", "required": true}
}';

SET @template := '{"agvCode":"1900","reqCode":"${randomHex(16)}","taskTyp":"${taskTyp}","wbCode":"006800BB031000"}';

-- result_mode = CALLBACK：一问两答。
--   一问：POST genAgvSchedulingTask
--   一答：同步受理应答 {"code":0,"data":"Task01_xxx","message":"成功","reqCode":"..."}
--         —— 平台记进 device_command.response_json，命令转 WAITING
--   二答：AGV 跑完通知平台，由回调入口结算，流程节点才继续
-- 之前是 SYNC_RETURN（一问一答），一答回来就判终态，AGV 实际还没跑完。
UPDATE device_action
SET param_schema     = @schema,
    request_template = @template,
    result_mode      = 'CALLBACK',
    updater          = 'yinan'
WHERE action_code = 'genAgvSchedulingTask'
  AND device_type_code = 'AGV'
  AND deleted = 0;

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
SELECT action_code,
       result_mode,
       request_path,
       param_schema,
       request_template
FROM device_action
WHERE action_code = 'genAgvSchedulingTask' AND device_type_code = 'AGV';

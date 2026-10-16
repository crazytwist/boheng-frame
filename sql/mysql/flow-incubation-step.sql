-- =====================================================================
-- Flow 能力目录：培养节点（第一个节点）
-- 背景：三个区流程的第一个节点「培养」，落到 RA-Incubation（孵育区）。
--       动作统一 PULL_TASK（拉取任务），培养时长参数经 defaultParams 传给设备端。
-- 参数：培养时间 = {"Seconds": null}（Seconds 键，null 为默认待填，运行时填秒数）。
-- 幂等：按 step_key 存在性判断后插入。
-- 主键：雪花 id（应用侧生成，非自增），显式给定。
-- =====================================================================

INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params, timeout_ms, max_attempts, status, tenant_id)
SELECT 7515390911610621259, 'incubation', '培养', 'device.task',
       '{"deviceCode":"RA-Incubation","actionCode":"PULL_TASK"}',
       '{"Seconds":null}',
       86400000, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM flow_step WHERE step_key = 'incubation' AND deleted = b'0');

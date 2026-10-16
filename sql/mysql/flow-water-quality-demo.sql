-- ----------------------------------------------------------------------------
-- 水质检测示例数据（Flow v3 端到端演示）
--
-- 场景：一次水质检测任务，96 个样本
--   串行：登记(bean) -> 分样(bean) -> 预处理(device.command)
--   并行：pH 检测(device.command) ∥ 浊度检测(device.command) -> 理化汇总(bean, ALL_SUCCESS 汇合)
--   异步：COD 测定(device.task) ∥ 氨氮测定(device.task) —— 机器端拉取队列执行后回报
--   循环：滴定(device.command) -> 轮次检查(bean)，while 表达式由检查节点驱动，平行测定 2 轮
--   子流程：重金属检测 FANOUT 扇出——A 组 48 个走 1 号线、B 组 48 个走 2 号线，
--          每条线 = 备样(bean) -> ICP-MS 检测(device.command)，扇出点即汇合点
--   收尾：总报告(bean)
--
-- ★ 设备类型建模：device_type_code 的取值来自 system_dict 的 device_type 字典（见 device-dict.sql）。
--   本 demo 每种仪器一个类型（PREP_STATION/pH_METER/TURBIDIMETER/TITRATOR/ICP_MS/
--   COD_ANALYZER/AMMONIA_ANALYZER），每类只挂自己的动作 —— 「能力集匹配」的区分度来源，
--   一台仪器只认领它真正会做的任务类型。
--
-- ★ device.task 异步链路（本 demo 新增 COD / 氨氮两个仪器任务）：
--   节点执行时把任务投进 flow_device_task 队列（PENDING），机器端拉取执行后回报，
--   回报经信号结算推进节点。与 device.command（同步推）正交。
--   · COD 绑定设备 WQ-COD-01；氨氮 deviceCode 留空 = 不限设备，机器端按 actionCode 抢
--   · 机器端联调（见文件末尾「机器端联调」段落）：
--       claim:    POST /app-api/flow/device-task/claim      {tenant-id 头, deviceCode 必填, actionCode?, workerId}
--       complete: POST /app-api/flow/device-task/complete   {taskNo, callbackToken, success, resultJson}
--
-- 依赖：device-dict.sql（设备类型字典）/ flow-definition.sql / flow-execution.sql /
--       flow-device-task.sql 已建表；设备域 device_* 表已建
-- 用法：灌入本脚本 -> 启动服务 -> 依次发布 wq_metal_line_a / wq_metal_line_b / wq_main
--       -> 以 definitionId 启动运行，入参 {"taskNo":"WQ-20261010-001","sampleCount":96}
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 1. 设备域：每类仪器一个独立 device_type_code + 动作 + 台账 + 仿真配置
--    （类型拆分是「能力集匹配」的区分度来源；全部走本进程仿真设备，SYNC 立即回）
-- ----------------------------------------------------------------------------
INSERT INTO device_action (id, device_type_code, action_code, action_name, source, result_mode, status, tenant_id, creator)
VALUES (910101, 'PREP_STATION',     'PREPARE',           '样品预处理', 'MANUAL', 'SYNC_RETURN', 0, 1, 'system'),
       (910102, 'PH_METER',         'MEASURE_PH',        'pH 测定',    'MANUAL', 'SYNC_RETURN', 0, 1, 'system'),
       (910103, 'TURBIDIMETER',     'MEASURE_TURBIDITY', '浊度测定',   'MANUAL', 'SYNC_RETURN', 0, 1, 'system'),
       (910104, 'TITRATOR',         'TITRATE',           '自动滴定',   'MANUAL', 'SYNC_RETURN', 0, 1, 'system'),
       (910105, 'ICP_MS',           'MEASURE_METAL',     '重金属检测', 'MANUAL', 'SYNC_RETURN', 0, 1, 'system'),
       -- 下面两个是 device.task 异步任务（走 flow_device_task 队列，机器端拉取执行后回报），
       -- 不经过 device_command 下发，result_mode 仅供台账语义（前端 actionCode 下拉数据源）
       (910106, 'COD_ANALYZER',     'MEASURE_COD',       'COD 测定',   'MANUAL', 'CALLBACK', 0, 1, 'system'),
       (910107, 'AMMONIA_ANALYZER', 'MEASURE_AMMONIA',   '氨氮测定',   'MANUAL', 'CALLBACK', 0, 1, 'system');

INSERT INTO device_info (id, device_code, device_name, device_type_code, model, vendor,
                         driver_type, status, connection_type, simulation_mode,
                         concurrency_policy, remark, tenant_id, creator)
VALUES (910201, 'WQ-PREP-01', '样品预处理台',   'PREP_STATION',     'WQ-PREP-300', '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：预处理台（仿真）', 1, 'system'),
       (910202, 'WQ-PH-01',   'pH 分析仪',      'PH_METER',         'WQ-PH-500',   '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：pH 计（仿真）', 1, 'system'),
       (910203, 'WQ-TB-01',   '浊度仪',         'TURBIDIMETER',     'WQ-TB-2100',  '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：浊度仪（仿真）', 1, 'system'),
       (910204, 'WQ-TIT-01',  '自动滴定仪',     'TITRATOR',         'WQ-TIT-90',   '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：滴定仪（仿真）', 1, 'system'),
       (910205, 'WQ-ICP-A',   '重金属分析仪 A', 'ICP_MS',           'WQ-ICP-MS',   '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：1 号检测线 ICP-MS（仿真）', 1, 'system'),
       (910206, 'WQ-ICP-B',   '重金属分析仪 B', 'ICP_MS',           'WQ-ICP-MS',   '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：2 号检测线 ICP-MS（仿真）', 1, 'system'),
       -- 两个 device.task 异步仪器（不入 device_command 链路，机器端从队列拉取执行）
       (910207, 'WQ-COD-01',  'COD 消解仪',     'COD_ANALYZER',     'WQ-COD-300',  '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：COD 消解仪（device.task 异步队列，仿真）', 1, 'system'),
       (910208, 'WQ-NH3-01',  '氨氮分析仪',     'AMMONIA_ANALYZER', 'WQ-NH3-100',  '伯珩仿真', 'HTTP', 'ONLINE', 'HTTP', b'1',
        'EXCLUSIVE', '水质检测示例：氨氮分析仪（device.task 异步队列，仿真）', 1, 'system');

-- 仿真报文：SYNC 立即回；${xxx} 取下发请求里的参数，演示参数真实往返
INSERT INTO device_simulate_profile (id, device_id, device_code, action_code, return_mode,
                                     async_wait_ms, poll_interval_ms, poll_max_times,
                                     response_body, status, tenant_id, creator)
VALUES (910301, 910201, 'WQ-PREP-01', 'PREPARE', 'SYNC', 0, 1000, 30,
        '{"status":"DONE","prepId":"PRP-${taskNo}","prepared":${sampleCount}}', 0, 1, 'system'),
       (910302, 910202, 'WQ-PH-01', 'MEASURE_PH', 'SYNC', 0, 1000, 30,
        '{"status":"DONE","ph":7.35,"phUnit":"","sampleCount":${sampleCount}}', 0, 1, 'system'),
       (910303, 910203, 'WQ-TB-01', 'MEASURE_TURBIDITY', 'SYNC', 0, 1000, 30,
        '{"status":"DONE","turbidity":1.28,"unit":"NTU","sampleCount":${sampleCount}}', 0, 1, 'system'),
       (910304, 910204, 'WQ-TIT-01', 'TITRATE', 'SYNC', 0, 1000, 30,
        '{"status":"DONE","volume":24.6,"unit":"mL","taskNo":"${taskNo}"}', 0, 1, 'system'),
       (910305, 910205, 'WQ-ICP-A', 'MEASURE_METAL', 'SYNC', 0, 1000, 30,
        '{"status":"DONE","group":"${group}","sampleCount":${sampleCount},"metals":{"Pb":0.008,"Cd":0.002},"unit":"mg/L"}', 0, 1, 'system'),
       (910306, 910206, 'WQ-ICP-B', 'MEASURE_METAL', 'SYNC', 0, 1000, 30,
        '{"status":"DONE","group":"${group}","sampleCount":${sampleCount},"metals":{"Pb":0.011,"Cd":0.003},"unit":"mg/L"}', 0, 1, 'system');

-- ----------------------------------------------------------------------------
-- 2. 能力目录（flow_step）：设备能力 × 8（6 个 device.command + 2 个 device.task），Bean 能力 × 6
-- ----------------------------------------------------------------------------
INSERT INTO flow_step (id, step_key, name, step_type, type_config, default_params,
                       timeout_ms, max_attempts, remark, status, tenant_id, creator)
VALUES (910401, 'wq_register',      '样品登记',   'bean.invoke',    '{"beanName":"waterRegisterAction"}', NULL, 60000, 1,
        '按任务号生成样本清单', 0, 1, 'system'),
       (910402, 'wq_split',         '样本分组',   'bean.invoke',    '{"beanName":"waterSplitAction"}', NULL, 60000, 1,
        '样本对半拆 A/B 两组', 0, 1, 'system'),
       (910403, 'wq_prepare',       '样品预处理', 'device.command', '{"deviceCode":"WQ-PREP-01","actionCode":"PREPARE"}', NULL, 300000, 1,
        '预处理台 WQ-PREP-01', 0, 1, 'system'),
       (910404, 'wq_ph_measure',    'pH 测定',    'device.command', '{"deviceCode":"WQ-PH-01","actionCode":"MEASURE_PH"}', NULL, 300000, 1,
        'pH 分析仪 WQ-PH-01', 0, 1, 'system'),
       (910405, 'wq_turbidity',     '浊度测定',   'device.command', '{"deviceCode":"WQ-TB-01","actionCode":"MEASURE_TURBIDITY"}', NULL, 300000, 1,
        '浊度仪 WQ-TB-01', 0, 1, 'system'),
       (910406, 'wq_merge',         '理化汇总',   'bean.invoke',    '{"beanName":"waterMergeAction"}', NULL, 60000, 1,
        'pH + 浊度合格判定（GB 5749-2022）', 0, 1, 'system'),
       (910407, 'wq_titrate',       '自动滴定',   'device.command', '{"deviceCode":"WQ-TIT-01","actionCode":"TITRATE"}', NULL, 300000, 1,
        '自动滴定仪 WQ-TIT-01', 0, 1, 'system'),
       (910408, 'wq_titrate_check', '滴定轮次检查', 'bean.invoke',  '{"beanName":"waterTitrationCheckAction","argsJson":"{\\\"targetRounds\\\":2}"}', NULL, 60000, 1,
        '产出 needMore 驱动循环，平行测定 2 轮', 0, 1, 'system'),
       (910409, 'wq_metal_prep',    '重金属备样', 'bean.invoke',    '{"beanName":"waterMetalPrepAction"}', NULL, 60000, 1,
        '清点扇出分来的本组样本（两条检测线复用）', 0, 1, 'system'),
       (910410, 'wq_metal_a',       '重金属检测 A 线', 'device.command', '{"deviceCode":"WQ-ICP-A","actionCode":"MEASURE_METAL"}', NULL, 300000, 1,
        '1 号检测线 ICP-MS', 0, 1, 'system'),
       (910411, 'wq_metal_b',       '重金属检测 B 线', 'device.command', '{"deviceCode":"WQ-ICP-B","actionCode":"MEASURE_METAL"}', NULL, 300000, 1,
        '2 号检测线 ICP-MS', 0, 1, 'system'),
       (910412, 'wq_report',        '检测总报告', 'bean.invoke',    '{"beanName":"waterReportAction"}', NULL, 60000, 1,
        '汇合理化 / 滴定 / 重金属 / 异步仪器产出报告', 0, 1, 'system'),
       -- device.task 异步仪器任务（机器端拉取队列执行后回报；deviceCode 可空=不限设备）
       (910413, 'wq_cod',           'COD 测定',   'device.task',    '{"deviceCode":"WQ-COD-01","actionCode":"MEASURE_COD"}', NULL, 300000, 1,
        'COD 消解仪 WQ-COD-01（异步队列，机器端拉取执行后回报）', 0, 1, 'system'),
       (910414, 'wq_ammonia',       '氨氮测定',   'device.task',    '{"actionCode":"MEASURE_AMMONIA"}', NULL, 300000, 1,
        '氨氮分析仪（deviceCode 空=不限设备，机器端按 actionCode 抢）', 0, 1, 'system');

-- ----------------------------------------------------------------------------
-- 3. 子流程：wq_metal_line_a（1 号检测线：备样 -> ICP-MS 检测）
-- ----------------------------------------------------------------------------
INSERT INTO flow_definition (id, pipeline_key, version, name, remark, status, tenant_id, creator)
VALUES (910501, 'wq_metal_line_a', 1, '重金属检测线 A', '水质检测示例：A 组样本（前 48 个）的检测线', 'DRAFT', 1, 'system');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type,
                       type_config, input_mapping, output_mapping, join_policy, tenant_id, creator)
VALUES (910601, 910501, 910409, 'metal_prep_a',    'A 线备样', 0, '[]', NULL, NULL, NULL, NULL, NULL, 1, 'system'),
       (910602, 910501, 910410, 'metal_measure_a', 'A 线检测', 1, '["metal_prep_a"]', NULL, NULL,
        '[{"param":"group","source":"NODE","nodeId":"metal_prep_a","field":"group"},
          {"param":"sampleCount","source":"NODE","nodeId":"metal_prep_a","field":"sampleCount"}]',
        '{"body":"raw"}', 'ALL_SUCCESS', 1, 'system');

-- ----------------------------------------------------------------------------
-- 4. 子流程：wq_metal_line_b（2 号检测线：备样 -> ICP-MS 检测）
-- ----------------------------------------------------------------------------
INSERT INTO flow_definition (id, pipeline_key, version, name, remark, status, tenant_id, creator)
VALUES (910502, 'wq_metal_line_b', 1, '重金属检测线 B', '水质检测示例：B 组样本（后 48 个）的检测线', 'DRAFT', 1, 'system');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type,
                       type_config, input_mapping, output_mapping, join_policy, tenant_id, creator)
VALUES (910611, 910502, 910409, 'metal_prep_b',    'B 线备样', 0, '[]', NULL, NULL, NULL, NULL, NULL, 1, 'system'),
       (910612, 910502, 910411, 'metal_measure_b', 'B 线检测', 1, '["metal_prep_b"]', NULL, NULL,
        '[{"param":"group","source":"NODE","nodeId":"metal_prep_b","field":"group"},
          {"param":"sampleCount","source":"NODE","nodeId":"metal_prep_b","field":"sampleCount"}]',
        '{"body":"raw"}', 'ALL_SUCCESS', 1, 'system');

-- ----------------------------------------------------------------------------
-- 5. 主流程：wq_main（串行 -> 并行 -> 汇合 -> 循环 -> 子流程扇出 -> 报告）
-- ----------------------------------------------------------------------------
INSERT INTO flow_definition (id, pipeline_key, version, name, remark, status, tenant_id, creator)
VALUES (910503, 'wq_main', 1, '水质检测主流程',
        '水质检测示例：登记 -> 分样 -> 预处理 -> (pH ∥ 浊度) -> 理化汇总 -> (COD ∥ 氨氮异步任务) -> 循环滴定 -> 重金属扇出 -> 总报告',
        'DRAFT', 1, 'system');

INSERT INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type,
                       type_config, input_mapping, output_mapping, run_config, join_policy,
                       join_failure_policy, branches, tenant_id, creator)
VALUES -- 串行：登记 -> 分样 -> 预处理
       (910621, 910503, 910401, 'register', '样品登记', 0, '[]', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 'system'),
       (910622, 910503, 910402, 'split',    '样本分组', 1, '["register"]', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 'system'),
       (910623, 910503, 910403, 'pretreat', '样品预处理', 2, '["split"]', NULL, NULL,
        '[{"param":"taskNo","source":"INPUT","field":"taskNo"},
          {"param":"sampleCount","source":"INPUT","field":"sampleCount"}]',
        '{"commandNo":"prepCommandNo"}', NULL, NULL, NULL, NULL, 1, 'system'),
       -- 并行：pH ∥ 浊度（都只依赖预处理）
       (910624, 910503, 910404, 'ph_test',        'pH 测定',  3, '["pretreat"]', NULL, NULL,
        '[{"param":"taskNo","source":"INPUT","field":"taskNo"},
          {"param":"sampleCount","source":"INPUT","field":"sampleCount"}]',
        '{"body":"raw"}', NULL, NULL, NULL, NULL, 1, 'system'),
       (910625, 910503, 910405, 'turbidity_test', '浊度测定', 4, '["pretreat"]', NULL, NULL,
        '[{"param":"taskNo","source":"INPUT","field":"taskNo"},
          {"param":"sampleCount","source":"INPUT","field":"sampleCount"}]',
        '{"body":"raw"}', NULL, NULL, NULL, NULL, 1, 'system'),
       -- 汇合：ALL_SUCCESS
       (910626, 910503, 910406, 'merge_check', '理化汇总', 5, '["ph_test","turbidity_test"]', NULL, NULL,
        '[{"param":"phRaw","source":"NODE","nodeId":"ph_test","field":"raw"},
          {"param":"tbRaw","source":"NODE","nodeId":"turbidity_test","field":"raw"}]',
        '{"ph":"ph","turbidity":"turbidity","qualified":"qualified","standard":"standard"}',
        NULL, 'ALL_SUCCESS', NULL, NULL, 1, 'system'),
       -- 异步仪器任务：COD ∥ 氨氮（device.task 队列，与滴定并行，机器端拉取执行后回报）
       --   output_mapping 把机器端回报 resultJson 的 cod/unit 映到节点输出，供总报告消费
       (910632, 910503, 910413, 'cod_test',     'COD 测定',  6, '["merge_check"]', NULL, NULL,
        '[{"param":"taskNo","source":"INPUT","field":"taskNo"},
          {"param":"sampleCount","source":"INPUT","field":"sampleCount"}]',
        '{"cod":"cod","unit":"unit"}', NULL, NULL, NULL, NULL, 1, 'system'),
       (910633, 910503, 910414, 'ammonia_test', '氨氮测定',  7, '["merge_check"]', NULL, NULL,
        '[{"param":"taskNo","source":"INPUT","field":"taskNo"},
          {"param":"sampleCount","source":"INPUT","field":"sampleCount"}]',
        '{"ammonia":"ammonia","unit":"unit"}', NULL, NULL, NULL, NULL, 1, 'system'),
       -- 循环体：滴定 -> 轮次检查（bodyEntry 到 bodyExit 按 sort_no 的闭区间）
       (910627, 910503, 910407, 'titrate',       '自动滴定',   8, '["merge_check"]', NULL, NULL,
        '[{"param":"taskNo","source":"INPUT","field":"taskNo"}]',
        '{"body":"raw"}', NULL, NULL, NULL, NULL, 1, 'system'),
       (910628, 910503, 910408, 'titrate_check', '滴定轮次检查', 9, '["titrate"]', NULL, NULL, NULL,
        '{"round":"round","needMore":"needMore","volume":"volume"}',
        NULL, NULL, NULL, NULL, 1, 'system'),
       -- 循环容器：while 由轮次检查节点驱动
       (910629, 910503, NULL, 'titration_loop', '循环滴定', 10, '["merge_check"]', 'loop',
        '{"bodyEntryNodeId":"titrate","bodyExitNodeId":"titrate_check","maxIterations":5,
          "whileExpression":"node.titrate_check.needMore == true"}',
        NULL, NULL, NULL, NULL, NULL, NULL, 1, 'system'),
       -- 子流程扇出：A/B 两组各建一个子执行，扇出点即汇合点
       (910630, 910503, NULL, 'heavy_dispatch', '重金属扇出', 11, '["titration_loop"]', 'subflow',
        '{"dispatchMode":"FANOUT","dispatch":{"kind":"bean","beanName":"waterGroupDispatchAction"},
          "completionPolicy":"ALL_SUCCESS"}',
        NULL, NULL, NULL, NULL, NULL, NULL, 1, 'system'),
       -- 收尾：等循环、扇出、两个异步仪器任务都成功
       (910631, 910503, 910412, 'report', '检测总报告', 12, '["titration_loop","heavy_dispatch","cod_test","ammonia_test"]', NULL, NULL,
        '[{"param":"iterations","source":"NODE","nodeId":"titration_loop","field":"iterations"},
          {"param":"metalLines","source":"NODE","nodeId":"heavy_dispatch","field":"children"}]',
        NULL, NULL, 'ALL_SUCCESS', NULL, NULL, 1, 'system');

-- ----------------------------------------------------------------------------
-- 6. 机器端联调：device.task 异步仪器任务（COD / 氨氮）如何推进
--
--   主流程跑到 cod_test / ammonia_test 时，节点执行会把任务写进 flow_device_task（PENDING），
--   流程挂起等 DEVICE_CALLBACK。机器端（或手工 curl 模拟）按下面三步推进：
--
--   (1) 认领一条待执行任务（tenant-id 头必带，机器端知道自己的租户）：
--       deviceCode 必传（机器端声明要操作哪台设备），平台按它的设备类型反查能力集匹配任务。
--       COD 消解仪认领（actionCode 可选，这里显式只拿 COD）：
--       curl -X POST 'http://localhost:48080/app-api/flow/device-task/claim' \
--         -H 'Content-Type: application/json' -H 'tenant-id: 1' \
--         -d '{"deviceCode":"WQ-COD-01","actionCode":"MEASURE_COD","workerId":"robot-arm-3"}'
--       氨氮分析仪认领（不传 actionCode，取能力集内任意类型；氨氮任务 device_code 为空也能匹配到）：
--       curl -X POST 'http://localhost:48080/app-api/flow/device-task/claim' \
--         -H 'Content-Type: application/json' -H 'tenant-id: 1' \
--         -d '{"deviceCode":"WQ-NH3-01","workerId":"robot-arm-3"}'
--       响应 data 里有 taskNo + callbackToken，机器端执行真实检测，然后回报。
--
--   (2) 回报执行结果（success=true 时 resultJson 会写进节点输出，供总报告消费）：
--       COD 仪器回报：  resultJson = {"status":"DONE","cod":2.4,"unit":"mg/L"}
--       氨氮仪器回报：  resultJson = {"status":"DONE","ammonia":0.12,"unit":"mg/L"}
--       curl -X POST 'http://localhost:48080/app-api/flow/device-task/complete' \
--         -H 'Content-Type: application/json' -H 'tenant-id: 1' \
--         -d '{"taskNo":"<认领到的 taskNo>","callbackToken":"<认领到的 callbackToken>","success":true,"resultJson":"{\"status\":\"DONE\",\"cod\":2.4,\"unit\":\"mg/L\"}"}'
--
--   (3) 回报通过后，引擎结算 cod_test / ammonia_test 节点，等到报告节点前置（滴定循环、
--       重金属扇出、两个异步任务）全部成功，总报告把 cod / ammonia 收进 asyncInstrument 段落。
--
--   注意：超时（节点 timeout_ms=300000，默认 5 分钟）未回报，Sweeper 会把任务置 FAILED，
--         节点按失败结算，整个运行按 FAIL_FAST 失败。
-- ----------------------------------------------------------------------------

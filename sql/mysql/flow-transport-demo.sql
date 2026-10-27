-- Transport 多批次装载循环示例
--
-- 运行前提：已执行 flow-definition.sql、flow-execution.sql 及相关迁移脚本。
-- 本脚本提供一套可直接发布/启动测试的流程定义：
--   准备轮次 -> 预约 AGV/仓储出料位 -> 释放本轮资源 -> 判断是否继续装载
--   循环结束后 -> 交接确认 -> WMS 更新位置
--
-- 当前六个 Bean 是测试适配器：只返回标准节点输出，不调用真实 AGV/WMS。
-- 后续将 Bean 内部替换为 Transport 领域服务和配置化外部适配器。

INSERT IGNORE INTO flow_step (id, step_key, name, step_type, type_config, default_params,
                       timeout_ms, max_attempts, remark, status, tenant_id, creator)
VALUES
 (930401, 'transport_prepare_cycle', 'Transport 准备装载轮次', 'bean.invoke',
  '{"beanName":"transportPrepareCycleAction"}', NULL, 60000, 1, '准备本轮待装载物料', 0, 1, 'system'),
 (930402, 'transport_reserve_resource', 'Transport 预约资源', 'bean.invoke',
  '{"beanName":"transportReserveResourceAction"}', NULL, 60000, 3, '预约 AGV 和 Warehouse 出料位', 0, 1, 'system'),
 (930403, 'transport_release_resource', 'Transport 释放资源', 'bean.invoke',
  '{"beanName":"transportReleaseResourceAction"}', NULL, 60000, 1, '释放本轮出料位资源', 0, 1, 'system'),
 (930404, 'transport_decide_next', 'Transport 判断下一步', 'bean.invoke',
  '{"beanName":"transportDecideNextAction"}', NULL, 60000, 1, '根据剩余物料和 AGV 容量判断是否继续装载', 0, 1, 'system'),
 (930405, 'transport_confirm_handover', 'Transport 确认交接', 'bean.invoke',
  '{"beanName":"transportConfirmHandoverAction"}', NULL, 60000, 2, '确认出料端和接收端双边交接', 0, 1, 'system'),
 (930406, 'transport_update_wms', 'Transport 更新 WMS', 'bean.invoke',
  '{"beanName":"transportUpdateWmsAction"}', NULL, 60000, 2, '交接成功后更新物料位置', 0, 1, 'system');

INSERT IGNORE INTO flow_definition (id, pipeline_key, version, name, remark, status, tenant_id, creator)
VALUES (930501, 'transport.multi_batch.execute', 1, 'Transport 多批次运输测试流程',
        '多批次装载循环：准备轮次 -> 预约资源 -> 释放资源 -> 判断继续；循环完成后交接并更新 WMS',
        'DRAFT', 1, 'system');

INSERT IGNORE INTO flow_node (id, definition_id, step_id, node_id, name, sort_no, depends_on, step_type,
                       type_config, input_mapping, output_mapping, run_config, join_policy,
                       join_failure_policy, branches, tenant_id, creator)
VALUES
 (930601, 930501, 930401, 'prepare_cycle', '准备装载轮次', 0, '[]', NULL, NULL,
  NULL, '{"cycleStatus":"cycleStatus","cycleNo":"cycleNo","remainingItemCount":"remainingItemCount","continueLoading":"continueLoading"}', NULL, NULL, NULL, NULL, 1, 'system'),
 (930602, 930501, 930402, 'reserve_resource', '预约 AGV 和出料位', 1, '["prepare_cycle"]', NULL, NULL,
  NULL, '{"resourceStatus":"resourceStatus","agvCode":"agvCode","outputSlotCode":"outputSlotCode"}', NULL, NULL, NULL, NULL, 1, 'system'),
 (930603, 930501, 930403, 'release_resource', '释放本轮资源', 2, '["reserve_resource"]', NULL, NULL,
  NULL, '{"resourceStatus":"releasedResourceStatus","released":"released"}', NULL, NULL, NULL, NULL, 1, 'system'),
 (930604, 930501, 930404, 'decide_next', '判断是否继续装载', 3, '["release_resource"]', NULL, NULL,
  NULL, '{"continueLoading":"continueLoading","nextAction":"nextAction"}', NULL, NULL, NULL, NULL, 1, 'system'),
 (930605, 930501, NULL, 'load_cycle_loop', '多批次装载循环', 4, '[]', 'loop',
  '{"bodyEntryNodeId":"prepare_cycle","bodyExitNodeId":"decide_next","maxIterations":20,"whileExpression":"node.decide_next.continueLoading == true"}',
  NULL, NULL, NULL, NULL, NULL, NULL, 1, 'system'),
 (930606, 930501, 930405, 'confirm_handover', '确认目标端交接', 5, '["load_cycle_loop"]', NULL, NULL,
  NULL, '{"handoverStatus":"handoverStatus","providerConfirmed":"providerConfirmed","receiverConfirmed":"receiverConfirmed"}', NULL, NULL, NULL, NULL, 1, 'system'),
 (930607, 930501, 930406, 'update_wms', '更新 WMS 位置', 6, '["confirm_handover"]', NULL, NULL,
  NULL, '{"wmsStatus":"wmsStatus","movementId":"movementId","locationUpdated":"locationUpdated"}', NULL, NULL, NULL, NULL, 1, 'system');

-- 测试启动入参示例：
-- {
--   "transportOrderId": 10001,
--   "loadCycleId": 20001,
--   "cycleNo": 1,
--   "remainingItemCount": 3,
--   "continueLoading": false,
--   "agvCode": "AGV-01",
--   "outputSlotCode": "OUT-01",
--   "movementId": "WMS-MOVE-001"
-- }

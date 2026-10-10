package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowBranchDecisionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowDefinitionMapper;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowBranchDecisionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.step.FlowStepMapper;
import cn.boheng.frame.module.flow.engine.execution.scheduler.BranchResolver;
import cn.boheng.frame.module.flow.engine.execution.scheduler.DagScheduler;
import cn.boheng.frame.module.flow.engine.expression.ExpressionEvaluator;
import cn.boheng.frame.module.flow.engine.signal.FlowSignalDispatcher;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeRegistry;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程执行引擎。一次运行的生命周期都在这里推进：
 * 启动（冻结图 + 初始化节点行）→ 就绪判定 → CAS 认领 → 分发执行 → 结算 → 再推进，
 * 直到全部节点终态后收尾。并行是就绪判定的天然结果，不需要 fork 节点；
 * 循环由引擎按迭代展开子图；子流程由引擎创建子执行并等 CHILD 信号汇合。
 *
 * <p>并发安全：节点认领用条件更新（PENDING → RUNNING），
 * 多线程同时推进同一运行时，只有一个能认领成功，其余空转返回。
 */
@Component
public class FlowExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionEngine.class);

    /**
     * 运行表
     */
    @Resource
    private FlowExecutionMapper executionMapper;

    /**
     * 节点执行表
     */
    @Resource
    private FlowStepExecutionMapper stepExecutionMapper;

    /**
     * 流程版本表
     */
    @Resource
    private FlowDefinitionMapper definitionMapper;

    /**
     * 节点表
     */
    @Resource
    private FlowNodeMapper nodeMapper;

    /**
     * 步骤表
     */
    @Resource
    private FlowStepMapper stepMapper;

    /**
     * 分支决策表
     */
    @Resource
    private FlowBranchDecisionMapper branchDecisionMapper;

    /**
     * 类型注册表
     */
    @Resource
    private StepTypeRegistry typeRegistry;

    /**
     * 上下文
     */
    @Resource
    private ExecutionContextHolder contextHolder;

    /**
     * 表达式求值器
     */
    @Resource
    private ExpressionEvaluator evaluator;

    /**
     * 分支激活与裁剪
     */
    @Resource
    private BranchResolver branchResolver;

    /**
     * 异步执行器（设备等阻塞步骤）
     */
    @Resource
    private FlowExecutionWorker worker;

    /**
     * 信号服务（CHILD 信号的发送方）
     */
    @Resource
    private FlowSignalDispatcher signalDispatcher;

    // ==================== 启动 ====================

    /**
     * 启动一次运行：冻结定义、初始化全部节点行（迭代 0、尝试 1）、写启动入参，随即推进。
     *
     * @return 运行编号
     */
    public Long start(FlowDefinitionDO definition, String inputParamsJson,
                      String triggerType, String triggeredBy, String businessKey,
                      Long parentExecutionId, String parentNodeId, Integer parentAttempt, Integer parentGroupIndex) {
        PipelineGraph graph = freeze(definition);
        FlowExecutionDO execution = FlowExecutionDO.builder()
                .executionNo(FlowConstants.EXECUTION_NO_PREFIX + "_" + IdUtil.getSnowflakeNextIdStr())
                .pipelineKey(definition.getPipelineKey())
                .version(definition.getVersion())
                .definitionId(definition.getId())
                .checksum(definition.getChecksum())
                .status("RUNNING")
                .triggerType(triggerType)
                .triggeredBy(triggeredBy)
                .businessKey(businessKey)
                .parentExecutionId(parentExecutionId)
                .parentNodeId(parentNodeId)
                .parentAttempt(parentAttempt)
                .parentGroupIndex(parentGroupIndex)
                .inputParams(inputParamsJson)
                .startedAt(LocalDateTime.now())
                .rowVersion(0)
                .build();
        executionMapper.insert(execution);
        // 启动入参进上下文
        Map<String, Object> input = StrUtil.isBlank(inputParamsJson)
                ? Map.of() : JSONUtil.parseObj(inputParamsJson);
        contextHolder.saveInput(execution.getId(), input);
        contextHolder.saveSystem(execution.getId(), "executionNo", execution.getExecutionNo());
        contextHolder.saveSystem(execution.getId(), "businessKey", StrUtil.nullToEmpty(businessKey));
        // 顶层节点（不在任何 loop body 内）建初始行；loop body 的行由 loop 驱动时展开
        Set<String> bodyNodes = collectLoopBodyNodes(graph);
        for (RuntimeNode node : graph.getNodes().values()) {
            if (bodyNodes.contains(node.getNodeId())) {
                continue;
            }
            insertStepRow(execution.getId(), node, 0, 1, "PENDING");
        }
        advance(execution.getId());
        return execution.getId();
    }

    /**
     * 取消：未开始的节点全部 CANCELLED，运行标 CANCELLED。
     * 正在执行的设备命令由设备域收口，节点行等它返回后按终态落库。
     */
    public void cancel(Long executionId) {
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
            return;
        }
        List<FlowStepExecutionDO> rows = stepExecutionMapper.selectByExecutionId(executionId);
        for (FlowStepExecutionDO row : rows) {
            if ("PENDING".equals(row.getStatus())) {
                row.setStatus("CANCELLED");
                row.setFinishedAt(LocalDateTime.now());
                stepExecutionMapper.updateById(row);
            }
        }
        finalizeExecution(executionId, "CANCELLED", null, null);
    }

    // ==================== 推进 ====================

    /**
     * 推进一轮：就绪判定 → 认领 → 分发 → 循环收口检查 → 终态检查。
     * 幂等，可被任何事件（节点完成 / 信号 / 启动）反复触发。
     */
    public void advance(Long executionId) {
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
            return;
        }
        PipelineGraph graph = loadGraph(execution);
        List<FlowStepExecutionDO> rows = stepExecutionMapper.selectByExecutionId(executionId);
        Map<String, FlowStepExecutionDO> latest = latestByNode(rows);
        DagScheduler.Decision decision = DagScheduler.decide(graph, latest);
        // 前置判失败：按 join_failure_policy 裁剪或置 DEAD
        decision.skipByFailure().forEach((nodeId, reason) -> {
            FlowStepExecutionDO row = latest.get(nodeId);
            if (row == null) {
                return;
            }
            RuntimeNode node = graph.node(nodeId);
            boolean skip = node != null && DagScheduler.shouldSkip(StrUtil.blankToDefault(
                    node.getJoinFailurePolicy(), FlowConstants.DEFAULT_JOIN_FAILURE_POLICY));
            row.setStatus(skip ? "SKIPPED" : "DEAD");
            row.setSkipReason(skip ? reason : null);
            row.setErrorCode(skip ? null : "FLOW_JOIN_FAILED");
            row.setErrorMessage(skip ? null : "前置未按汇合策略满足");
            row.setFinishedAt(LocalDateTime.now());
            stepExecutionMapper.updateById(row);
        });
        // 认领并分发就绪节点
        for (String nodeId : decision.readyNodeIds()) {
            FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId,
                    latest.get(nodeId).getIterationNo());
            if (row == null || !claim(row)) {
                continue;
            }
            dispatch(graph, execution, row);
        }
        // 循环收口：body 全部终态的 loop 求值是否再来一轮
        driveLoops(graph, execution, latest);
        // 循环驱动可能刚创建了下一轮 PENDING 行；立即再认领一次并分发
        List<FlowStepExecutionDO> refreshed = stepExecutionMapper.selectByExecutionId(executionId);
        Map<String, FlowStepExecutionDO> latestAfterLoop = latestByNode(refreshed);
        DagScheduler.Decision loopDecision = DagScheduler.decide(graph, latestAfterLoop);
        for (String nodeId : loopDecision.readyNodeIds()) {
            FlowStepExecutionDO latestRow = latestAfterLoop.get(nodeId);
            if (latestRow == null) {
                continue;
            }
            FlowStepExecutionDO claimRow = stepExecutionMapper.selectLatestAttempt(executionId, nodeId,
                    latestRow.getIterationNo());
            if (claimRow == null || !claim(claimRow)) {
                continue;
            }
            dispatch(graph, execution, claimRow);
        }
        // 终态检查
        checkFinish(executionId, graph);
    }

    /**
     * CAS 认领：PENDING → RUNNING，只有一个线程能成功
     */
    private boolean claim(FlowStepExecutionDO row) {
        FlowStepExecutionDO patch = new FlowStepExecutionDO();
        patch.setStatus("RUNNING");
        patch.setStartedAt(LocalDateTime.now());
        return stepExecutionMapper.update(patch,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FlowStepExecutionDO>()
                        .eq(FlowStepExecutionDO::getId, row.getId())
                        .eq(FlowStepExecutionDO::getStatus, "PENDING")) == 1;
    }

    /**
     * 分发一个已认领的节点。设备等阻塞类型交给 Worker；轻量类型当场执行
     */
    private void dispatch(PipelineGraph graph, FlowExecutionDO execution, FlowStepExecutionDO row) {
        RuntimeNode node = graph.node(row.getNodeId());
        if (node == null) {
            settle(row, StepOutcome.failed("节点不在图里"));
            return;
        }
        if (FlowConstants.STEP_LOOP.equals(node.getStepType())) {
            driveLoopBegin(graph, execution, node, row);
            return;
        }
        if (FlowConstants.STEP_SUBFLOW.equals(node.getStepType())) {
            driveSubflow(graph, execution, node, row);
            return;
        }
        if (FlowConstants.STEP_DEVICE_COMMAND.equals(node.getStepType())) {
            worker.submitDeviceStep(graph, execution, row, node);
            return;
        }
        // 轻量类型（route/script/bean/manual/timer/notification）当场执行
        StepOutcome outcome = executeStep(graph, execution, row, node);
        settle(row, outcome);
    }

    /**
     * 执行一个轻量节点：解析参数 → 取执行器 → 执行
     */
    public StepOutcome executeStep(PipelineGraph graph, FlowExecutionDO execution,
                                   FlowStepExecutionDO row, RuntimeNode node) {
        Map<String, Object> env = contextHolder.loadEnv(execution.getId());
        Map<String, Object> params = contextHolder.resolveParams(node, env);
        StepExecutor executor = typeRegistry.getExecutor(node.getStepType());
        if (executor == null) {
            return StepOutcome.failed("类型没有执行器: " + node.getStepType());
        }
        Object config = typeRegistry.parseConfig(node.getStepType(), node.getTypeConfig());
        long timeoutMs = effectiveTimeout(graph, node);
        row.setInputSnapshot(params.isEmpty() ? null : JSONUtil.toJsonStr(params));
        return executor.execute(new StepExecutionContext(execution.getId(), row.getNodeId(),
                row.getAttempt(), row.getIterationNo(), params, config, timeoutMs));
    }

    // ==================== 结算 ====================

    /**
     * 结算一个节点：SUCCESS 写输出进上下文；FAILED 判重试；WAITING 落等待信息；
     * BRANCH 落分支决策并激活/裁剪。结算完触发下一轮推进。
     */
    public void settle(FlowStepExecutionDO row, StepOutcome outcome) {
        FlowExecutionDO execution = executionMapper.selectById(row.getExecutionId());
        if (execution == null) {
            return;
        }
        PipelineGraph graph = loadGraph(execution);
        RuntimeNode node = graph.node(row.getNodeId());
        switch (outcome.getKind()) {
            case SUCCESS -> {
                row.setStatus("SUCCESS");
                row.setOutputData(outcome.getOutputJson());
                row.setFinishedAt(LocalDateTime.now());
                stepExecutionMapper.updateById(row);
                contextHolder.saveNodeOutput(row.getExecutionId(), row.getNodeId(),
                        row.getIterationNo(), outcome.getOutputJson(),
                        node == null ? null : node.getOutputMapping());
            }
            case FAILED -> {
                int maxAttempts = effectiveMaxAttempts(graph, node, row);
                if (row.getAttempt() < maxAttempts && isRetryable(node)) {
                    row.setStatus("FAILED");
                    row.setErrorMessage(outcome.getErrorMessage());
                    row.setFinishedAt(LocalDateTime.now());
                    stepExecutionMapper.updateById(row);
                    insertStepRow(row.getExecutionId(), node, row.getIterationNo(), row.getAttempt() + 1, "PENDING");
                } else {
                    row.setStatus("DEAD");
                    row.setErrorMessage(outcome.getErrorMessage());
                    row.setFinishedAt(LocalDateTime.now());
                    stepExecutionMapper.updateById(row);
                    if (node == null || !FlowConstants.FAIL_SKIP.equals(StrUtil.nullToEmpty(node.runFailureStrategy()))) {
                        // FAIL_FAST：整条运行失败，未开始的节点全部取消
                        failExecution(row.getExecutionId(), "FLOW_STEP_DEAD",
                                "节点【" + row.getNodeId() + "】失败且次数用尽: "
                                        + StrUtil.nullToEmpty(outcome.getErrorMessage()));
                        return;
                    }
                }
            }
            case WAITING -> {
                row.setStatus("WAITING");
                row.setWaitingFor(outcome.getWaitingFor());
                row.setWaitRef(outcome.getWaitRef());
                row.setDeadlineAt(deadlineOf(outcome, row));
                stepExecutionMapper.updateById(row);
            }
            case BRANCH -> {
                row.setStatus("SUCCESS");
                row.setSelectedBranch(outcome.getBranchKey());
                row.setFinishedAt(LocalDateTime.now());
                stepExecutionMapper.updateById(row);
                recordDecision(graph, node, row, outcome.getBranchKey());
                branchResolver.apply(graph, node, outcome.getBranchKey(), row.getIterationNo(), row);
            }
            default -> {
            }
        }
        advance(row.getExecutionId());
    }

    /**
     * 等待截止时刻：TIMER 的 waitRef 就是到期时刻文本；设备等待按节点超时
     */
    private LocalDateTime deadlineOf(StepOutcome outcome, FlowStepExecutionDO row) {
        if ("TIMER".equals(outcome.getWaitingFor()) && StrUtil.isNotBlank(outcome.getWaitRef())) {
            return LocalDateTime.parse(outcome.getWaitRef());
        }
        return null;
    }

    /**
     * 落一条分支决策。决策快照带上当时的上下文，事后能回答「为什么走了这条路」
     */
    private void recordDecision(PipelineGraph graph, RuntimeNode node, FlowStepExecutionDO row, String branchKey) {
        Map<String, Object> env = contextHolder.loadEnv(row.getExecutionId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("selected", branchKey);
        snapshot.put("context", env);
        FlowBranchDecisionDO decision = FlowBranchDecisionDO.builder()
                .executionId(row.getExecutionId())
                .routeNodeId(row.getNodeId())
                .attempt(row.getAttempt())
                .iterationNo(row.getIterationNo())
                .selectedBranch(branchKey)
                .matchMode("EXCLUSIVE")
                .activatedTargets(JSONUtil.toJsonStr(node.getBranches() == null
                        ? List.of() : node.getBranches().getOrDefault(branchKey, List.of())))
                .decisionData(JSONUtil.toJsonStr(snapshot))
                .build();
        branchDecisionMapper.insert(decision);
    }

    // ==================== 循环驱动 ====================

    /**
     * loop 节点开始：展开 body 节点的第 0 轮行
     */
    private void driveLoopBegin(PipelineGraph graph, FlowExecutionDO execution, RuntimeNode loopNode,
                                FlowStepExecutionDO loopRow) {
        List<RuntimeNode> body = loopBody(graph, loopNode);
        if (body.isEmpty()) {
            loopRow.setStatus("SUCCESS");
            loopRow.setFinishedAt(LocalDateTime.now());
            stepExecutionMapper.updateById(loopRow);
            advance(execution.getId());
            return;
        }
        for (RuntimeNode bodyNode : body) {
            insertStepRow(execution.getId(), bodyNode, 0, 1, "PENDING");
        }
        advance(execution.getId());
    }

    /**
     * 循环收口：RUNNING 的 loop 节点，body 当前轮全部终态时求值是否再来一轮
     */
    private void driveLoops(PipelineGraph graph, FlowExecutionDO execution, Map<String, FlowStepExecutionDO> latest) {
        for (RuntimeNode node : graph.getNodes().values()) {
            if (!FlowConstants.STEP_LOOP.equals(node.getStepType())) {
                continue;
            }
            FlowStepExecutionDO loopRow = latest.get(node.getNodeId());
            if (loopRow == null || !"RUNNING".equals(loopRow.getStatus())) {
                continue;
            }
            List<RuntimeNode> body = loopBody(graph, node);
            if (body.isEmpty()) {
                loopRow.setStatus("SUCCESS");
                loopRow.setFinishedAt(LocalDateTime.now());
                stepExecutionMapper.updateById(loopRow);
                continue;
            }
            // body 当前轮（各行最大迭代）是否全部终态
            int currentIteration = body.stream()
                    .map(bn -> latest.get(bn.getNodeId()))
                    .filter(r -> r != null)
                    .mapToInt(FlowStepExecutionDO::getIterationNo)
                    .max().orElse(0);
            boolean allTerminal = body.stream()
                    .map(bn -> stepExecutionMapper.selectLatestAttempt(execution.getId(), bn.getNodeId(), currentIteration))
                    .allMatch(r -> r != null && DagScheduler.isTerminal(r));
            if (!allTerminal) {
                continue;
            }
            // 一轮跑完：求值是否再来一轮
            cn.boheng.frame.module.flow.engine.type.config.LoopConfig config =
                    (cn.boheng.frame.module.flow.engine.type.config.LoopConfig) typeRegistry.parseConfig(FlowConstants.STEP_LOOP, node.getTypeConfig());
            boolean again = false;
            if (config != null && config.getMaxIterations() != null
                    && currentIteration + 1 < config.getMaxIterations()) {
                if (StrUtil.isNotBlank(config.getWhileExpression())) {
                    Map<String, Object> env = contextHolder.loadEnv(execution.getId());
                    try {
                        again = evaluator.evalBool(config.getWhileExpression(), env);
                    } catch (RuntimeException ex) {
                        log.warn("[flow][loop] 循环条件求值失败，按退出处理: {}", ex.getMessage());
                    }
                }
            }
            if (again) {
                for (RuntimeNode bodyNode : body) {
                    insertStepRow(execution.getId(), bodyNode, currentIteration + 1, 1, "PENDING");
                }
            } else {
                String outputJson = JSONUtil.toJsonStr(Map.of("iterations", currentIteration + 1));
                loopRow.setStatus("SUCCESS");
                loopRow.setOutputData(outputJson);
                loopRow.setFinishedAt(LocalDateTime.now());
                stepExecutionMapper.updateById(loopRow);
                // 循环轮数进上下文，下游节点按参数映射取 node.{loop}.iterations
                contextHolder.saveNodeOutput(execution.getId(), node.getNodeId(), 0,
                        outputJson, "{\"iterations\":\"iterations\"}");
            }
        }
    }

    /**
     * loop body 节点：bodyEntry 到 bodyExit 按 sortNo 的闭区间
     */
    private List<RuntimeNode> loopBody(PipelineGraph graph, RuntimeNode loopNode) {
        cn.boheng.frame.module.flow.engine.type.config.LoopConfig config =
                (cn.boheng.frame.module.flow.engine.type.config.LoopConfig) typeRegistry.parseConfig(FlowConstants.STEP_LOOP, loopNode.getTypeConfig());
        if (config == null || StrUtil.isBlank(config.getBodyEntryNodeId())
                || StrUtil.isBlank(config.getBodyExitNodeId())) {
            return List.of();
        }
        List<RuntimeNode> sorted = graph.getNodes().values().stream()
                .filter(n -> n.getSortNo() != null)
                .sorted(java.util.Comparator.comparingInt(RuntimeNode::getSortNo))
                .toList();
        List<RuntimeNode> body = new ArrayList<>();
        boolean inBody = false;
        for (RuntimeNode n : sorted) {
            if (config.getBodyEntryNodeId().equals(n.getNodeId())) {
                inBody = true;
            }
            if (inBody) {
                body.add(n);
            }
            if (inBody && config.getBodyExitNodeId().equals(n.getNodeId())) {
                break;
            }
        }
        return body;
    }

    // ==================== 子流程驱动 ====================

    /**
     * subflow 节点分发：SINGLE 建一个子执行；FANOUT 算计划后幂等建 N 个子执行。
     * 父节点进 WAITING/CHILD，子执行终态时发信号汇合。
     */
    private void driveSubflow(PipelineGraph graph, FlowExecutionDO execution, RuntimeNode node,
                              FlowStepExecutionDO row) {
        cn.boheng.frame.module.flow.engine.type.config.SubflowConfig config =
                (cn.boheng.frame.module.flow.engine.type.config.SubflowConfig) typeRegistry.parseConfig(FlowConstants.STEP_SUBFLOW, node.getTypeConfig());
        if (config == null) {
            settle(row, StepOutcome.failed("子流程节点缺少配置"));
            return;
        }
        boolean fanout = "FANOUT".equals(StrUtil.blankToDefault(config.getDispatchMode(), "SINGLE"));
        List<cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry> plan;
        if (fanout) {
            plan = dispatchPlan(graph, execution, node, row, config);
        } else {
            plan = List.of(entryOf(config.getPipelineKey(), config.getVersion(), config.getInputJson()));
        }
        if (plan == null) {
            return; // 分发 Bean 失败已结算
        }
        if (plan.isEmpty()) {
            // 空计划：无组可拆，节点直接成功
            String outputJson = JSONUtil.toJsonStr(Map.of("children", 0));
            row.setStatus("SUCCESS");
            row.setOutputData(outputJson);
            row.setFinishedAt(LocalDateTime.now());
            stepExecutionMapper.updateById(row);
            contextHolder.saveNodeOutput(execution.getId(), node.getNodeId(), 0,
                    outputJson, "{\"children\":\"children\"}");
            advance(execution.getId());
            return;
        }
        int created = 0;
        for (int i = 0; i < plan.size(); i++) {
            cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry entry = plan.get(i);
            FlowDefinitionDO child = resolveChildDefinition(entry.getPipelineKey(), entry.getVersion());
            if (child == null) {
                settle(row, StepOutcome.failed("子流程不存在或未发布: " + entry.getPipelineKey()));
                return;
            }
            // 幂等：同一父节点同一组只建一次。business_key = 运行:节点:尝试:组号
            String businessKey = execution.getId() + ":" + node.getNodeId() + ":" + row.getAttempt() + ":" + i;
            FlowExecutionDO existing = findByBusinessKey(businessKey);
            if (existing == null) {
                start(child, entry.getInputJson(), "CHILD", StrUtil.nullToEmpty(execution.getTriggeredBy()),
                        businessKey, execution.getId(), node.getNodeId(), row.getAttempt(),
                        fanout ? i : null);
                created++;
            }
        }
        row.setStatus("WAITING");
        row.setWaitingFor("CHILD");
        row.setWaitRef(execution.getId() + ":" + node.getNodeId() + ":" + row.getAttempt());
        row.setOutputData(JSONUtil.toJsonStr(Map.of("children", plan.size())));
        stepExecutionMapper.updateById(row);
    }

    /**
     * FANOUT：调分发 Bean 算计划。Bean 失败时结算父节点为失败
     */
    private List<cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry> dispatchPlan(
            PipelineGraph graph, FlowExecutionDO execution, RuntimeNode node, FlowStepExecutionDO row,
            cn.boheng.frame.module.flow.engine.type.config.SubflowConfig config) {
        if (config.getDispatch() == null || StrUtil.isBlank(config.getDispatch().getBeanName())) {
            settle(row, StepOutcome.failed("扇出节点缺少分发 Bean"));
            return null;
        }
        cn.boheng.frame.module.flow.engine.dispatch.FlowDispatchAction action =
                dispatchRegistry().get(config.getDispatch().getBeanName());
        if (action == null) {
            settle(row, StepOutcome.failed("分发 Bean 未注册: " + config.getDispatch().getBeanName()));
            return null;
        }
        Map<String, Object> env = contextHolder.loadEnv(execution.getId());
        try {
            List<cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry> plan = action.dispatch(env);
            if (plan != null && plan.size() > FlowConstants.MAX_FANOUT_GROUPS) {
                settle(row, StepOutcome.failed("扇出组数超过上限 " + FlowConstants.MAX_FANOUT_GROUPS));
                return null;
            }
            return plan == null ? List.of() : plan;
        } catch (RuntimeException ex) {
            settle(row, StepOutcome.failed("分发 Bean 执行失败: " + ex.getMessage()));
            return null;
        }
    }

    /**
     * 子执行终态发来的 CHILD 信号。SINGLE 直接结算；FANOUT 收齐后按完成策略结算
     */
    public void onChildFinished(Long parentExecutionId, String parentNodeId, Integer parentAttempt,
                                Integer groupIndex, boolean childSuccess) {
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(parentExecutionId, parentNodeId, 0);
        if (row == null || !"WAITING".equals(row.getStatus()) || !row.getAttempt().equals(parentAttempt)) {
            return;
        }
        FlowExecutionDO parent = executionMapper.selectById(parentExecutionId);
        if (parent == null) {
            return;
        }
        List<FlowExecutionDO> children = executionMapper.selectByParent(parentExecutionId, parentNodeId, parentAttempt);
        boolean allTerminal = children.stream().allMatch(c -> !"RUNNING".equals(c.getStatus())
                && !"PENDING".equals(c.getStatus()));
        if (!allTerminal) {
            return;
        }
        boolean anySuccess = children.stream().anyMatch(c -> "SUCCESS".equals(c.getStatus()));
        boolean allSuccess = children.stream().allMatch(c -> "SUCCESS".equals(c.getStatus()));
        FlowDefinitionDO definition = definitionMapper.selectById(parent.getDefinitionId());
        PipelineGraph graph = definition == null ? null : freeze(definition);
        RuntimeNode node = graph == null ? null : graph.node(parentNodeId);
        String policy = "ANY_SUCCESS";
        if (node != null) {
            cn.boheng.frame.module.flow.engine.type.config.SubflowConfig config =
                    (cn.boheng.frame.module.flow.engine.type.config.SubflowConfig) typeRegistry.parseConfig(FlowConstants.STEP_SUBFLOW, node.getTypeConfig());
            policy = config == null ? "ALL_SUCCESS"
                    : StrUtil.blankToDefault(config.getCompletionPolicy(), "ALL_SUCCESS");
        }
        boolean ok = "ANY_SUCCESS".equals(policy) ? anySuccess : allSuccess;
        List<Map<String, Object>> summary = new ArrayList<>();
        children.forEach(c -> summary.add(Map.of(
                "executionId", c.getId(),
                "executionNo", c.getExecutionNo(),
                "groupIndex", c.getParentGroupIndex() == null ? -1 : c.getParentGroupIndex(),
                "status", c.getStatus())));
        String outputJson = JSONUtil.toJsonStr(Map.of("children", children.size(), "detail", summary));
        row.setStatus(ok ? "SUCCESS" : "DEAD");
        row.setOutputData(outputJson);
        if (!ok) {
            row.setErrorCode("FLOW_CHILD_FAILED");
            row.setErrorMessage("子流程未按完成策略全部成功");
        }
        row.setFinishedAt(LocalDateTime.now());
        stepExecutionMapper.updateById(row);
        // 扇出汇合结果进上下文，下游节点按参数映射取 node.{subflow}.children
        if (ok) {
            contextHolder.saveNodeOutput(parentExecutionId, parentNodeId, 0,
                    outputJson, "{\"children\":\"children\"}");
        }
        advance(parentExecutionId);
    }

    // ==================== 收尾 ====================

    /**
     * 终态检查：全部节点行终态 → 收尾运行。子执行收尾时给父发 CHILD 信号
     */
    private void checkFinish(Long executionId, PipelineGraph graph) {
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
            return;
        }
        List<FlowStepExecutionDO> rows = stepExecutionMapper.selectByExecutionId(executionId);
        boolean allTerminal = rows.stream().allMatch(DagScheduler::isTerminal);
        if (!allTerminal) {
            return;
        }
        boolean hasDead = rows.stream().anyMatch(r -> "DEAD".equals(r.getStatus())
                || "FAILED".equals(r.getStatus()));
        if (hasDead) {
            finalizeExecution(executionId, "FAILED", "FLOW_STEP_DEAD", "存在失败节点");
            return;
        }
        // 汇总各顶层节点输出
        Map<String, Object> output = new LinkedHashMap<>();
        for (FlowStepExecutionDO row : rows) {
            if ("SUCCESS".equals(row.getStatus()) && StrUtil.isNotBlank(row.getOutputData())) {
                output.put(row.getNodeId(), JSONUtil.parseObj(row.getOutputData()));
            }
        }
        execution.setOutputData(JSONUtil.toJsonStr(output));
        executionMapper.updateById(execution);
        finalizeExecution(executionId, "SUCCESS", null, null);
    }

    /**
     * 整条运行失败：未开始的节点取消，运行标 FAILED
     */
    private void failExecution(Long executionId, String errorCode, String errorMessage) {
        List<FlowStepExecutionDO> rows = stepExecutionMapper.selectByExecutionId(executionId);
        for (FlowStepExecutionDO row : rows) {
            if ("PENDING".equals(row.getStatus())) {
                row.setStatus("CANCELLED");
                row.setFinishedAt(LocalDateTime.now());
                stepExecutionMapper.updateById(row);
            }
        }
        finalizeExecution(executionId, "FAILED", errorCode, errorMessage);
    }

    /**
     * 运行收尾：写终态。是子执行时给父节点发 CHILD 信号
     */
    private void finalizeExecution(Long executionId, String status, String errorCode, String errorMessage) {
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null) {
            return;
        }
        execution.setStatus(status);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        log.info("[flow][execution] 运行结束: {} -> {}", execution.getExecutionNo(), status);
        // 子执行：通知父节点
        if (execution.getParentExecutionId() != null && execution.getParentNodeId() != null
                && execution.getParentAttempt() != null) {
            signalDispatcher.dispatchChildSignal(execution, "SUCCESS".equals(status));
        }
    }

    // ==================== 工具 ====================

    /**
     * 冻结定义：加载节点行 + 能力行构建图
     */
    public PipelineGraph freeze(FlowDefinitionDO definition) {
        List<FlowNodeDO> nodeRows = nodeMapper.selectByDefinitionId(definition.getId());
        Map<Long, FlowStepDO> steps = new HashMap<>();
        for (FlowNodeDO nodeRow : nodeRows) {
            if (nodeRow.getStepId() != null && !steps.containsKey(nodeRow.getStepId())) {
                FlowStepDO step = stepMapper.selectById(nodeRow.getStepId());
                if (step != null) {
                    steps.put(step.getId(), step);
                }
            }
        }
        return PipelineGraph.of(definition.getPipelineKey(), definition.getVersion(),
                definition.getId(), definition.getChecksum(), nodeRows, steps);
    }

    /**
     * 按运行加载冻结图
     */
    private PipelineGraph loadGraph(FlowExecutionDO execution) {
        FlowDefinitionDO definition = definitionMapper.selectById(execution.getDefinitionId());
        return definition == null ? new PipelineGraph() : freeze(definition);
    }

    /**
     * 每节点当前迭代的最新尝试行
     */
    private Map<String, FlowStepExecutionDO> latestByNode(List<FlowStepExecutionDO> rows) {
        Map<String, FlowStepExecutionDO> latest = new HashMap<>();
        for (FlowStepExecutionDO row : rows) {
            FlowStepExecutionDO current = latest.get(row.getNodeId());
            if (current == null || row.getIterationNo() > current.getIterationNo()
                    || (row.getIterationNo().equals(current.getIterationNo())
                        && row.getAttempt() > current.getAttempt())) {
                latest.put(row.getNodeId(), row);
            }
        }
        return latest;
    }

    /**
     * 建一行节点执行
     */
    private void insertStepRow(Long executionId, RuntimeNode node, int iterationNo, int attempt, String status) {
        FlowStepExecutionDO row = FlowStepExecutionDO.builder()
                .executionId(executionId)
                .nodeId(node.getNodeId())
                .stepType(node.getStepType())
                .iterationNo(iterationNo)
                .attempt(attempt)
                .status(status)
                .queuedAt(LocalDateTime.now())
                .rowVersion(0)
                .build();
        try {
            stepExecutionMapper.insert(row);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // row_key 唯一索引上的幂等保护：并发 advance 看到同一轮 body 已结束时会重复插入；
            // 静默忽略，让先到的线程完成插入，后到的发现已存在则跳过
            log.debug("[flow][engine] 节点行已存在，跳过: exec={} node={} iter={} attempt={}",
                    executionId, node.getNodeId(), iterationNo, attempt);
        }
    }

    /**
     * 收集全部 loop body 内的节点编码（启动时这些行不建，由 loop 驱动展开）
     */
    private Set<String> collectLoopBodyNodes(PipelineGraph graph) {
        Set<String> bodyNodes = new java.util.HashSet<>();
        for (RuntimeNode node : graph.getNodes().values()) {
            if (FlowConstants.STEP_LOOP.equals(node.getStepType())) {
                bodyNodes.addAll(loopBody(graph, node).stream().map(RuntimeNode::getNodeId).toList());
            }
        }
        return bodyNodes;
    }

    /**
     * 生效超时：节点覆盖 &gt; 能力默认 &gt; 全局默认
     */
    private long effectiveTimeout(PipelineGraph graph, RuntimeNode node) {
        Long override = node.runTimeoutMs();
        if (override != null) {
            return override;
        }
        FlowStepDO step = graph.stepOf(node);
        return step != null && step.getTimeoutMs() != null
                ? step.getTimeoutMs() : FlowConstants.DEFAULT_TIMEOUT_MS;
    }

    /**
     * 生效尝试上限：节点覆盖 &gt; 能力默认 &gt; 1
     */
    private int effectiveMaxAttempts(PipelineGraph graph, RuntimeNode node, FlowStepExecutionDO row) {
        Integer override = node == null ? null : node.runMaxAttempts();
        if (override != null) {
            return override;
        }
        FlowStepDO step = graph.stepOf(node);
        return step != null && step.getMaxAttempts() != null
                ? step.getMaxAttempts() : FlowConstants.DEFAULT_MAX_ATTEMPTS;
    }

    /**
     * 类型是否可重试
     */
    private boolean isRetryable(RuntimeNode node) {
        if (node == null) {
            return false;
        }
        cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor descriptor =
                typeRegistry.getDescriptor(node.getStepType());
        return descriptor == null || descriptor.retryable();
    }

    /**
     * 按编码 + 版本取已发布的子流程定义。版本空取最新发布版
     */
    private FlowDefinitionDO resolveChildDefinition(String pipelineKey, Integer version) {
        FlowDefinitionDO child;
        if (version == null) {
            child = definitionMapper.selectPublishedLatest(pipelineKey);
        } else {
            child = definitionMapper.selectByKeyAndVersion(pipelineKey, version);
        }
        return child != null && FlowConstants.STATUS_PUBLISHED.equals(child.getStatus()) ? child : null;
    }

    /**
     * 按业务键找子执行（FANOUT 幂等）
     */
    private FlowExecutionDO findByBusinessKey(String businessKey) {
        return executionMapper.selectOne(FlowExecutionDO::getBusinessKey, businessKey);
    }

    /**
     * SINGLE 计划条目
     */
    private cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry entryOf(
            String pipelineKey, Integer version, String inputJson) {
        cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry entry =
                new cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry();
        entry.setPipelineKey(pipelineKey);
        entry.setVersion(version);
        entry.setInputJson(inputJson);
        return entry;
    }

    /**
     * 分发注册表（延迟注入避免循环依赖）
     */
    @Resource
    private cn.boheng.frame.module.flow.engine.dispatch.FlowDispatchRegistry dispatchRegistryBean;

    private cn.boheng.frame.module.flow.engine.dispatch.FlowDispatchRegistry dispatchRegistry() {
        return dispatchRegistryBean;
    }

}

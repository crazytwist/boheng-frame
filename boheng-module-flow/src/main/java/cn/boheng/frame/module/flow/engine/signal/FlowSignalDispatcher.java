package cn.boheng.frame.module.flow.engine.signal;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.device.api.DeviceCommandApi;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepSignalDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepSignalMapper;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.engine.execution.FlowExecutionEngine;
import cn.boheng.frame.module.flow.service.task.FlowDeviceTaskService;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandStatusEnum;
import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.ExecutionStatus;
import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.SignalType;
import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.StepStatus;
import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.WaitingReason;

/**
 * 信号分发。人工确认、定时到点、子流程终态都从统一入口进来：
 * 先落收件箱（signal_id 幂等），再定位目标节点行并结算。
 * 引擎与信号之间只靠 signalId / executionId+nodeId+attempt 定位，没有对象引用。
 */
@Component
public class FlowSignalDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FlowSignalDispatcher.class);

    /**
     * 信号收件箱
     */
    @Resource
    private FlowStepSignalMapper signalMapper;

    /**
     * 节点执行表
     */
    @Resource
    private FlowStepExecutionMapper stepExecutionMapper;

    /**
     * 运行表
     */
    @Resource
    private FlowExecutionMapper executionMapper;

    /**
     * 引擎。@Lazy 避免构造期循环依赖（引擎结算会触发推进，推进可能再收信号）
     */
    @Resource
    @Lazy
    private FlowExecutionEngine engine;

    /**
     * 设备异步任务服务
     */
    @Resource
    private FlowDeviceTaskService deviceTaskService;

    /**
     * 设备命令契约。用于查询完全异步下发命令的持久化终态
     */
    @Resource
    private DeviceCommandApi deviceCommandApi;

    /**
     * 节点级外部回调的令牌派生与校验
     */
    @Resource
    private FlowCallbackToken callbackToken;

    /**
     * 人工确认信号。页面上确认 / 拒绝都走这里
     *
     * @param success true 确认放行，false 拒绝（节点按失败结算）
     * @return 信号是否被接受（节点不在等待人工时拒绝）
     */
    public boolean manualSignal(Long executionId, String nodeId, boolean success, String comment, String operator) {
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.MANUAL.name().equals(row.getWaitingFor())) {
            return false;
        }
        String signalId = "manual_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return false; // 重复确认
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(),
                SignalType.MANUAL.name(), comment, success);
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution != null && ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            log.info("[flow] 信号结算 type=MANUAL node={} success={}", nodeId, success);
            engine.settle(row, success
                    ? StepOutcome.success("{\"confirmed\":true,\"comment\":\"" + safe(comment) + "\"}")
                    : StepOutcome.failed("人工拒绝: " + StrUtil.nullToEmpty(comment)));
        }
        return true;
    }

    /**
     * 外部业务系统回报入口。
     *
     * <p>回调方不带租户头，先忽略租户定位运行拿到 tenantId，再在租户上下文里结算；
     * 否则租户拦截器会给后续查询全部加上 tenant_id 条件而查不到行。</p>
     *
     * @return 信号是否被接受（运行不存在、令牌不对、节点已不在等待时拒绝）
     */
    public boolean externalCallback(Long executionId, String nodeId, Integer attempt, String token,
                                    boolean success, String resultJson, String errorMessage) {
        if (executionId == null || StrUtil.isBlank(nodeId)) {
            return false;
        }
        FlowExecutionDO execution = TenantUtils.executeIgnore(() -> executionMapper.selectById(executionId));
        if (execution == null || execution.getTenantId() == null) {
            return false;
        }
        return TenantUtils.execute(execution.getTenantId(),
                () -> externalCallbackSignal(executionId, nodeId, attempt, token, success, resultJson, errorMessage));
    }

    /**
     * 结算等待外部回调的节点。令牌绑定「执行 + 节点 + 尝试次数」，
     * 所以旧尝试的迟到回调会被挡下，不会误结算新一轮。
     */
    private boolean externalCallbackSignal(Long executionId, String nodeId, Integer attempt, String token,
                                           boolean success, String resultJson, String errorMessage) {
        FlowStepExecutionDO row = stepExecutionMapper.selectWaitingByNode(executionId, nodeId);
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.EXTERNAL_CONFIRMATION.name().equals(row.getWaitingFor())) {
            return false;
        }
        // attempt 显式给了就必须对上；不给也得用行上的值校验令牌
        if (attempt != null && !attempt.equals(row.getAttempt())) {
            log.warn("[flow][signal] 外部回调尝试次数不匹配: exe={} node={} expect={} got={}",
                    executionId, nodeId, row.getAttempt(), attempt);
            return false;
        }
        if (!callbackToken.matches(executionId, nodeId, row.getAttempt(), token)) {
            log.warn("[flow][signal] 外部回调令牌不匹配: exe={} node={}", executionId, nodeId);
            return false;
        }
        String signalId = "external_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return false; // 重复回调
        }
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            return false;
        }
        log.info("[flow] 信号结算 type=EXTERNAL node={} success={}", nodeId, success);
        boolean settled = engine.settle(row, success
                ? StepOutcome.success(StrUtil.blankToDefault(resultJson, "{\"confirmed\":true}"))
                : StepOutcome.failed(StrUtil.blankToDefault(errorMessage, "外部系统回报失败")));
        if (!settled) {
            return false;
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(),
                SignalType.EXTERNAL.name(), resultJson, success);
        return true;
    }

    /**
     * 外部回调超时。扫描器发现 EXTERNAL_CONFIRMATION 过期时调用，按失败结算。
     * 外部系统不回报就是没有结果，不能一直挂住整条流程。
     */
    public void externalCallbackTimeout(Long executionId, String nodeId, Integer attempt) {
        FlowStepExecutionDO row = stepExecutionMapper.selectWaitingByNode(executionId, nodeId);
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.EXTERNAL_CONFIRMATION.name().equals(row.getWaitingFor())) {
            return;
        }
        if (attempt != null && !attempt.equals(row.getAttempt())) {
            return;
        }
        String signalId = "external_timeout_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            return;
        }
        log.info("[flow] 信号结算 type=EXTERNAL_TIMEOUT node={}", nodeId);
        if (!engine.settle(row, StepOutcome.failed("等待外部系统回调超时"))) {
            return;
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(),
                SignalType.EXTERNAL.name(), null, false);
    }

    /**
     * 子执行终态信号。直接回调引擎的汇合判定
     */
    public void dispatchChildSignal(FlowExecutionDO child, boolean success) {        String signalId = "child_" + child.getId();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        save(signalId, child.getParentExecutionId(), child.getParentNodeId(), child.getParentAttempt(),
                0, SignalType.CHILD.name(), "{\"status\":\"" + child.getStatus() + "\"}", success);
        log.info("[flow] 信号结算 type=CHILD child={} parentNode={} success={}",
                child.getExecutionNo(), child.getParentNodeId(), success);
        engine.onChildFinished(child.getParentExecutionId(), child.getParentNodeId(),
                child.getParentAttempt(), child.getParentGroupIndex(), success);
    }

    /**
     * 定时到点信号。扫描器发现 TIMER 等待到期时调用
     */
    public void timerSignal(Long executionId, String nodeId, Integer attempt) {
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.TIMER.name().equals(row.getWaitingFor())) {
            return;
        }
        String signalId = "timer_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(),
                SignalType.TIMER.name(), null, true);
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution != null && ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            log.info("[flow] 信号结算 type=TIMER node={}", nodeId);
            engine.settle(row, StepOutcome.success("{\"waited\":true}"));
        }
    }

    /**
     * 设备异步任务回报信号。机器端执行完调 complete 接口，这里落信号并结算节点。
     *
     * @param taskNo        机器端回报的任务号
     * @param callbackToken 认领时下发的回报令牌，校验通过才推进
     * @param success       执行成功 / 失败
     * @param resultJson    成功结果 JSON（写进节点输出）
     * @param errorMessage  失败说明
     * @return 信号是否被接受（任务不存在、令牌不匹配或节点已不在等待时拒绝）
     */
    public boolean deviceTaskSignal(String taskNo, String callbackToken, boolean success, String resultJson, String errorMessage) {
        FlowDeviceTaskDO task = deviceTaskService.selectByTaskNo(taskNo);
        if (task == null) {
            return false;
        }
        if (!deviceTaskService.matchesToken(task, callbackToken)) {
            log.warn("[flow][signal] 设备任务回报令牌不匹配: taskNo={}", taskNo);
            return false;
        }
        Long executionId = task.getExecutionId();
        String nodeId = task.getNodeId();
        Integer attempt = task.getAttempt();
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.DEVICE_CALLBACK.name().equals(row.getWaitingFor())
                || !attempt.equals(row.getAttempt())) {
            return false;
        }
        String signalId = "devicetask_" + executionId + "_" + nodeId + "_" + attempt;
        if (signalMapper.selectBySignalId(signalId) != null) {
            return false; // 重复回报
        }
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            return false;
        }
        // 先抢任务行。抢不到说明超时或另一次回报已经收口，不能再结算，也不能占住信号号
        if (!deviceTaskService.finish(taskNo, success, resultJson, errorMessage)) {
            return false;
        }
        log.info("[flow] 信号结算 type=DEVICE taskNo={} node={} success={}", taskNo, nodeId, success);
        boolean settled = engine.settle(row, success
                ? StepOutcome.success(StrUtil.blankToDefault(resultJson, "{\"completed\":true}"))
                : StepOutcome.failed(StrUtil.blankToDefault(errorMessage, "设备任务失败")));
        if (!settled) {
            return false;
        }
        save(signalId, executionId, nodeId, attempt, row.getIterationNo(),
                SignalType.EXTERNAL.name(), resultJson, success);
        return true;
    }

    /**
     * 设备任务超时信号。扫描器发现 DEVICE_CALLBACK 到期时调用，按失败结算。
     */
    public void deviceTaskTimeout(Long executionId, String nodeId, Integer attempt) {
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.DEVICE_CALLBACK.name().equals(row.getWaitingFor())) {
            return;
        }
        String signalId = "devicetask_timeout_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            return;
        }
        if (!deviceTaskService.timeout(executionId, nodeId, row.getAttempt())) {
            return;
        }
        log.info("[flow] 信号结算 type=TIMEOUT node={}", nodeId);
        if (!engine.settle(row, StepOutcome.failed("设备任务超时"))) {
            return;
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(),
                SignalType.EXTERNAL.name(), null, false);
    }

    /**
     * 检查异步设备命令是否进入终态，并据此结算等待中的 device.command 节点。
     * 该入口可重复调用，信号编号和节点 CAS 共同保证幂等。
     *
     * @param row 等待设备命令回调的步骤执行行
     */
    public void deviceCommandSignal(FlowStepExecutionDO row) {
        if (row == null || !StepStatus.WAITING.name().equals(row.getStatus())
                || !WaitingReason.DEVICE_COMMAND_CALLBACK.name().equals(row.getWaitingFor())) {
            return;
        }
        Long commandId;
        try {
            commandId = row.getCommandId() != null ? row.getCommandId() : Long.valueOf(row.getWaitRef());
        } catch (NumberFormatException ex) {
            log.warn("[flow][signal] 设备命令引用不合法: {}", row.getWaitRef());
            return;
        }
        DeviceCommandResultDTO result = deviceCommandApi.getResult(commandId);
        if (result == null || CommandStatusEnum.ACQUIRED.getStatus().equals(result.getStatus())
                || CommandStatusEnum.SENT.getStatus().equals(result.getStatus())
                || CommandStatusEnum.WAITING.getStatus().equals(result.getStatus())) {
            return;
        }
        String signalId = "devicecommand_" + commandId;
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        FlowExecutionDO execution = executionMapper.selectById(row.getExecutionId());
        if (execution == null || !ExecutionStatus.RUNNING.name().equals(execution.getStatus())) {
            return;
        }
        boolean success = CommandStatusEnum.SUCCEEDED.getStatus().equals(result.getStatus());
        Map<String, Object> output = new LinkedHashMap<>();
        // 解析结果先平铺，保持既有流程的字段引用不失效
        String resultJson = StrUtil.blankToDefault(result.getParsedOutputJson(), result.getOutputJson());
        if (StrUtil.isNotBlank(resultJson) && JSONUtil.isTypeJSONObject(resultJson)) {
            JSONObject parsed = JSONUtil.parseObj(resultJson);
            output.putAll(parsed);
            output.put("data", parsed);
        } else {
            output.put("body", resultJson);
            output.put("data", null);
        }
        // 规范字段最后写：流程只看这几个，不需要知道厂商的 ExecuteStatus / ResultCode
        output.put("success", success);
        output.put("errorType", result.getErrorType());
        output.put("retryable", result.getRetryable());
        output.put("raw", result.getOutputJson());
        output.put("commandId", result.getCommandId());
        output.put("commandNo", result.getCommandNo());
        output.put("status", result.getStatus());
        String payload = JSONUtil.toJsonStr(output);
        boolean settled = engine.settle(row, success
                ? StepOutcome.success(payload)
                : StepOutcome.failed(StrUtil.blankToDefault(result.getErrorMessage(),
                        "设备命令结束：" + result.getStatus()), result.getRetryable()));
        if (!settled) {
            return;
        }
        save(signalId, row.getExecutionId(), row.getNodeId(), row.getAttempt(), row.getIterationNo(),
                SignalType.EXTERNAL.name(), payload, success);
    }

    /** 设备命令完成事件入口；找不到流程节点时视为人工/独立设备命令。 */
    public void deviceCommandSignal(Long commandId) {
        if (commandId == null) return;
        deviceCommandSignal(stepExecutionMapper.selectWaitingByCommandId(commandId));
    }

    /**
     * 落一行收件箱
     */
    private void save(String signalId, Long executionId, String nodeId, Integer attempt,
                      Integer iterationNo, String signalType, String payload, Boolean success) {
        FlowStepSignalDO signal = FlowStepSignalDO.builder()
                .signalId(signalId)
                .executionId(executionId)
                .nodeId(nodeId)
                .attempt(attempt)
                .iterationNo(iterationNo)
                .signalType(signalType)
                .payload(payload)
                .success(success)
                .processStatus("APPLIED")
                .receivedAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .build();
        try {
            signalMapper.insert(signal);
        } catch (RuntimeException ex) {
            // 唯一键冲突 = 重复信号，幂等忽略
            log.info("[flow][signal] 重复信号忽略: {}", signalId);
        }
    }

    /**
     * 备注里的引号转义，防 JSON 注入
     */
    private String safe(String text) {
        return text == null ? "" : text.replace("\"", "'");
    }

}

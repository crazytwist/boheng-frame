package cn.boheng.frame.module.flow.engine.signal;

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
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * 人工确认信号。页面上确认 / 拒绝都走这里
     *
     * @param success true 确认放行，false 拒绝（节点按失败结算）
     * @return 信号是否被接受（节点不在等待人工时拒绝）
     */
    public boolean manualSignal(Long executionId, String nodeId, boolean success, String comment, String operator) {
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !"WAITING".equals(row.getStatus()) || !"MANUAL".equals(row.getWaitingFor())) {
            return false;
        }
        String signalId = "manual_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return false; // 重复确认
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(), "MANUAL", comment, success);
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution != null && "RUNNING".equals(execution.getStatus())) {
            log.info("[flow] 信号结算 type=MANUAL node={} success={}", nodeId, success);
            engine.settle(row, success
                    ? StepOutcome.success("{\"confirmed\":true,\"comment\":\"" + safe(comment) + "\"}")
                    : StepOutcome.failed("人工拒绝: " + StrUtil.nullToEmpty(comment)));
        }
        return true;
    }

    /**
     * 子执行终态信号。直接回调引擎的汇合判定
     */
    public void dispatchChildSignal(FlowExecutionDO child, boolean success) {
        String signalId = "child_" + child.getId();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        save(signalId, child.getParentExecutionId(), child.getParentNodeId(), child.getParentAttempt(),
                0, "CHILD", "{\"status\":\"" + child.getStatus() + "\"}", success);
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
        if (row == null || !"WAITING".equals(row.getStatus()) || !"TIMER".equals(row.getWaitingFor())) {
            return;
        }
        String signalId = "timer_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(), "TIMER", null, true);
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution != null && "RUNNING".equals(execution.getStatus())) {
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
        return settleDeviceTask(task, success, resultJson, errorMessage);
    }

    /**
     * 机器端按 taskId 更新为已完成/失败时使用（无 callbackToken，与 {@code updateTaskStatus} 对齐）。
     */
    public boolean deviceTaskSignalTrusted(String taskNo, boolean success, String resultJson, String errorMessage) {
        FlowDeviceTaskDO task = deviceTaskService.selectByTaskNo(taskNo);
        if (task == null) {
            return false;
        }
        return settleDeviceTask(task, success, resultJson, errorMessage);
    }

    private boolean settleDeviceTask(FlowDeviceTaskDO task, boolean success, String resultJson, String errorMessage) {
        String taskNo = task.getTaskNo();
        Long executionId = task.getExecutionId();
        String nodeId = task.getNodeId();
        Integer attempt = task.getAttempt();
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !"WAITING".equals(row.getStatus()) || !"DEVICE_CALLBACK".equals(row.getWaitingFor())
                || !attempt.equals(row.getAttempt())) {
            return false;
        }
        String signalId = "devicetask_" + executionId + "_" + nodeId + "_" + attempt;
        if (signalMapper.selectBySignalId(signalId) != null) {
            return false; // 重复回报
        }
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
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
        save(signalId, executionId, nodeId, attempt, row.getIterationNo(), "EXTERNAL", resultJson, success);
        return true;
    }

    /**
     * 设备任务超时信号。扫描器发现 DEVICE_CALLBACK 到期时调用，按失败结算。
     */
    public void deviceTaskTimeout(Long executionId, String nodeId, Integer attempt) {
        FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(executionId, nodeId, 0);
        if (row == null || !"WAITING".equals(row.getStatus()) || !"DEVICE_CALLBACK".equals(row.getWaitingFor())) {
            return;
        }
        String signalId = "devicetask_timeout_" + executionId + "_" + nodeId + "_" + row.getAttempt();
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        FlowExecutionDO execution = executionMapper.selectById(executionId);
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
            return;
        }
        if (!deviceTaskService.timeout(executionId, nodeId, row.getAttempt())) {
            return;
        }
        log.info("[flow] 信号结算 type=TIMEOUT node={}", nodeId);
        if (!engine.settle(row, StepOutcome.failed("设备任务超时"))) {
            return;
        }
        save(signalId, executionId, nodeId, row.getAttempt(), row.getIterationNo(), "EXTERNAL", null, false);
    }

    /**
     * 检查异步设备命令是否进入终态，并据此结算等待中的 device.command 节点。
     * 该入口可重复调用，信号编号和节点 CAS 共同保证幂等。
     *
     * @param row 等待设备命令回调的步骤执行行
     */
    public void deviceCommandSignal(FlowStepExecutionDO row) {
        if (row == null || !"WAITING".equals(row.getStatus())
                || !"DEVICE_COMMAND_CALLBACK".equals(row.getWaitingFor())
                || StrUtil.isBlank(row.getWaitRef())) {
            return;
        }
        Long commandId;
        try {
            commandId = Long.valueOf(row.getWaitRef());
        } catch (NumberFormatException ex) {
            log.warn("[flow][signal] 设备命令引用不合法: {}", row.getWaitRef());
            return;
        }
        DeviceCommandResultDTO result = deviceCommandApi.getResult(commandId);
        if (result == null || "ACQUIRED".equals(result.getStatus()) || "SENT".equals(result.getStatus())
                || "WAITING".equals(result.getStatus())) {
            return;
        }
        String signalId = "devicecommand_" + commandId;
        if (signalMapper.selectBySignalId(signalId) != null) {
            return;
        }
        FlowExecutionDO execution = executionMapper.selectById(row.getExecutionId());
        if (execution == null || !"RUNNING".equals(execution.getStatus())) {
            return;
        }
        boolean success = "SUCCEEDED".equals(result.getStatus());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("commandId", result.getCommandId());
        output.put("commandNo", result.getCommandNo());
        output.put("status", result.getStatus());
        output.put("body", result.getOutputJson());
        String payload = JSONUtil.toJsonStr(output);
        boolean settled = engine.settle(row, success
                ? StepOutcome.success(payload)
                : StepOutcome.failed(StrUtil.blankToDefault(result.getErrorMessage(),
                        "设备命令结束：" + result.getStatus())));
        if (!settled) {
            return;
        }
        save(signalId, row.getExecutionId(), row.getNodeId(), row.getAttempt(), row.getIterationNo(),
                "EXTERNAL", payload, success);
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

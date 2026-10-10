package cn.boheng.frame.module.flow.engine.signal;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepSignalDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepSignalMapper;
import cn.boheng.frame.module.flow.engine.execution.FlowExecutionEngine;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
            engine.settle(row, StepOutcome.success("{\"waited\":true}"));
        }
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

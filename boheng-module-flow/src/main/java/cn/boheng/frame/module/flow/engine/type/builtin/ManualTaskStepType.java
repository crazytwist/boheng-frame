package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.ManualTaskConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * manual.task 人工确认。执行即挂起，等人在运行页确认；
 * 确认接口写一条 MANUAL 信号，由信号路由器结算节点。
 */
@Component
public class ManualTaskStepType implements StepTypeDescriptor, StepExecutor {

    @Override
    public String type() {
        return FlowConstants.STEP_MANUAL_TASK;
    }

    @Override
    public StepRole role() {
        return StepRole.WAIT;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.SIGNAL;
    }

    @Override
    public Class<?> configType() {
        return ManualTaskConfig.class;
    }

    @Override
    public boolean retryable() {
        // 人工节点被拒绝就是明确结论，重试等于再问一遍
        return false;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        // 说明可空：不填就让确认人看节点名称
        if (config != null && config instanceof ManualTaskConfig manual
                && manual.getInstruction() != null && manual.getInstruction().length() > 512) {
            issues.add(new FlowIssue(nodeId, "人工确认的说明不能超过 512 字"));
        }
    }

    /**
     * 执行：挂起等人工。waitRef 用 executionId:nodeId:attempt 便于确认接口定位
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        return StepOutcome.waiting("MANUAL", ctx.getExecutionId() + ":" + ctx.getNodeId() + ":" + ctx.getAttempt());
    }

}

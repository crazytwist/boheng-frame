package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.TimerWaitConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * timer.wait 定时等待。执行器算出到期时刻即返回等待，
 * 由后台扫描器到点发信号唤醒——不依赖外部定时组件，扫描粒度 30 秒。
 */
@Component
public class TimerWaitStepType implements StepTypeDescriptor, StepExecutor {

    @Override
    public String type() {
        return FlowConstants.STEP_TIMER_WAIT;
    }

    @Override
    public StepRole role() {
        return StepRole.WAIT;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.TIMER;
    }

    @Override
    public Class<?> configType() {
        return TimerWaitConfig.class;
    }

    @Override
    public boolean retryable() {
        return false;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof TimerWaitConfig timer)) {
            issues.add(new FlowIssue(nodeId, "定时节点缺少配置"));
            return;
        }
        if (timer.getWaitSeconds() == null) {
            issues.add(new FlowIssue(nodeId, "等待节点必须配置等待秒数"));
            return;
        }
        if (timer.getWaitSeconds() < 1
                || timer.getWaitSeconds() > FlowConstants.MAX_TIMEOUT_MS / 1000) {
            issues.add(new FlowIssue(nodeId, "等待秒数须在 1 秒到 24 小时之间"));
        }
    }

    /**
     * 执行：算出到期时刻写进 waitRef（ISO 文本），调度器按它落 deadline_at
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        TimerWaitConfig config = ctx.config();
        LocalDateTime deadline;
        deadline = LocalDateTime.now().plusSeconds(config.getWaitSeconds());
        return StepOutcome.waiting("TIMER", deadline.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

}

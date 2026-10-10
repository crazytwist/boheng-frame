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
import cn.hutool.core.util.StrUtil;
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

    /**
     * waitUntil 的格式
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        boolean hasSeconds = timer.getWaitSeconds() != null;
        boolean hasUntil = StrUtil.isNotBlank(timer.getWaitUntil());
        if (hasSeconds == hasUntil) {
            issues.add(new FlowIssue(nodeId, "定时节点要在等待秒数和指定时刻里二选一"));
            return;
        }
        if (hasSeconds && (timer.getWaitSeconds() < 1
                || timer.getWaitSeconds() > FlowConstants.MAX_TIMEOUT_MS / 1000)) {
            issues.add(new FlowIssue(nodeId, "等待秒数须在 1 秒到 24 小时之间"));
        }
        if (hasUntil) {
            try {
                LocalDateTime.parse(timer.getWaitUntil(), FORMATTER);
            } catch (RuntimeException ex) {
                issues.add(new FlowIssue(nodeId, "指定时刻的格式须是 yyyy-MM-dd HH:mm:ss"));
            }
        }
    }

    /**
     * 执行：算出到期时刻写进 waitRef（ISO 文本），调度器按它落 deadline_at
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        TimerWaitConfig config = ctx.config();
        LocalDateTime deadline;
        if (config.getWaitSeconds() != null) {
            deadline = LocalDateTime.now().plusSeconds(config.getWaitSeconds());
        } else {
            deadline = LocalDateTime.parse(config.getWaitUntil(), FORMATTER);
        }
        return StepOutcome.waiting("TIMER", deadline.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

}

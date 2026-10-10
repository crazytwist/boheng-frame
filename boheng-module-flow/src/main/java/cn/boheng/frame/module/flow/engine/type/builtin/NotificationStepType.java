package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.NotificationConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * notification.send 发通知。一期出口：结构化日志 + 事件内容写进节点输出
 * （前端时间线能看到）；真实渠道（站内信/邮件）由后续接入方扩展。
 */
@Component
public class NotificationStepType implements StepTypeDescriptor, StepExecutor {

    private static final Logger log = LoggerFactory.getLogger(NotificationStepType.class);

    @Override
    public String type() {
        return FlowConstants.STEP_NOTIFICATION_SEND;
    }

    @Override
    public StepRole role() {
        return StepRole.ACTION;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.SYNC;
    }

    @Override
    public Class<?> configType() {
        return NotificationConfig.class;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof NotificationConfig notice)) {
            issues.add(new FlowIssue(nodeId, "通知节点缺少配置"));
            return;
        }
        if (StrUtil.isBlank(notice.getContent())) {
            issues.add(new FlowIssue(nodeId, "通知节点要填写内容"));
        }
    }

    /**
     * 执行：记录通知事件。发送失败不阻断流程——通知是旁路动作
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        NotificationConfig config = ctx.config();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("title", StrUtil.blankToDefault(config.getTitle(), "流程通知"));
        event.put("content", config.getContent());
        event.put("receiverIds", config.getReceiverIds());
        event.put("executionId", ctx.getExecutionId());
        event.put("nodeId", ctx.getNodeId());
        log.info("[flow][notification] {}", JSONUtil.toJsonStr(event));
        return StepOutcome.success(JSONUtil.toJsonStr(event));
    }

}

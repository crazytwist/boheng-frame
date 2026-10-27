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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * notification.send 发通知。一期出口：结构化日志 + 事件内容写进节点输出
 * （前端时间线能看到）；真实渠道（站内信/邮件）由后续接入方扩展。
 */
@Component
public class NotificationStepType implements StepTypeDescriptor, StepExecutor {

    private static final Logger log = LoggerFactory.getLogger(NotificationStepType.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

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
        event.put("content", render(config.getContent(), ctx.getParams()));
        event.put("receiverIds", config.getReceiverIds());
        event.put("executionId", ctx.getExecutionId());
        event.put("nodeId", ctx.getNodeId());
        log.info("[flow][notification] {}", JSONUtil.toJsonStr(event));
        return StepOutcome.success(JSONUtil.toJsonStr(event));
    }

    /**
     * 使用当前节点已解析的入参替换通知内容中的 ${field} 占位符。
     */
    private static String render(String template, Map<String, Object> params) {
        if (StrUtil.isBlank(template) || params == null || params.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = params.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

}

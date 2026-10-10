package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.expression.ExpressionEvaluator;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.ScriptConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * script.run 求值一段 aviator 表达式。结果写进上下文 NODE 命名空间，
 * 后续节点和路由按 node.nodeId.resultKey 读取。一期只支持表达式，不引脚本引擎。
 */
@Component
public class ScriptStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 表达式求值器
     */
    private final ExpressionEvaluator evaluator;

    public ScriptStepType(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String type() {
        return FlowConstants.STEP_SCRIPT_RUN;
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
        return ScriptConfig.class;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof ScriptConfig script)) {
            issues.add(new FlowIssue(nodeId, "表达式节点缺少配置"));
            return;
        }
        if (StrUtil.isBlank(script.getResultKey())) {
            issues.add(new FlowIssue(nodeId, "表达式节点要写结果键名"));
        }
        if (StrUtil.isBlank(script.getExpression())) {
            issues.add(new FlowIssue(nodeId, "表达式节点要填写表达式"));
            return;
        }
        String error = evaluator.checkSyntax(script.getExpression());
        if (error != null) {
            issues.add(new FlowIssue(nodeId, "表达式不合法：" + error));
        }
    }

    /**
     * 执行：求值并包成 {resultKey: value} 的 JSON 写上下文
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        ScriptConfig config = ctx.config();
        try {
            Object value = evaluator.eval(config.getExpression(), ctx.getParams());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put(config.getResultKey(), value);
            return StepOutcome.success(JSONUtil.toJsonStr(output));
        } catch (RuntimeException ex) {
            return StepOutcome.failed("表达式求值失败: " + ex.getMessage());
        }
    }

}

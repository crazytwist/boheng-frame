package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.expression.ExpressionEvaluator;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.LoopConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * loop 循环容器。把 bodyEntry 到 bodyExit 之间的一段子图跑 N 轮，
 * 定义上没有回边——由调度器 LoopDriver 在执行期接管迭代，不配执行器。
 */
@Component
public class LoopStepType implements StepTypeDescriptor {

    /**
     * 表达式求值器。whileExpression 用
     */
    private final ExpressionEvaluator evaluator;

    public LoopStepType(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String type() {
        return FlowConstants.STEP_LOOP;
    }

    @Override
    public StepRole role() {
        return StepRole.CONTROL;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.SYNC;
    }

    @Override
    public Class<?> configType() {
        return LoopConfig.class;
    }

    @Override
    public boolean retryable() {
        // 容器失败时重试整个循环没有意义，轮次内的节点自己有重试
        return false;
    }

    /**
     * 发布期校验由 FlowGraphChecker 补齐区间与图关系（需要看全图），这里只校验标量
     */
    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof LoopConfig loop)) {
            issues.add(new FlowIssue(nodeId, "循环节点缺少配置"));
            return;
        }
        if (StrUtil.isBlank(loop.getBodyEntryNodeId()) || StrUtil.isBlank(loop.getBodyExitNodeId())) {
            issues.add(new FlowIssue(nodeId, "循环节点要写明循环体的入口和出口节点"));
        }
        if (loop.getMaxIterations() == null || loop.getMaxIterations() < 1
                || loop.getMaxIterations() > FlowConstants.MAX_ITERATIONS_LIMIT) {
            issues.add(new FlowIssue(nodeId,
                    "循环轮次上限须在 1 到 " + FlowConstants.MAX_ITERATIONS_LIMIT + " 之间"));
        }
        if (StrUtil.isNotBlank(loop.getWhileExpression())) {
            String error = evaluator.checkSyntax(loop.getWhileExpression());
            if (error != null) {
                issues.add(new FlowIssue(nodeId, "循环条件不合法：" + error));
            }
        }
    }

}

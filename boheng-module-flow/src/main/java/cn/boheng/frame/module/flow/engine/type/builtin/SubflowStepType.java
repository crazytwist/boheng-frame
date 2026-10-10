package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.dispatch.FlowDispatchRegistry;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.SubflowConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * subflow 子流程。SINGLE 引用一个子流程创建一个子执行；
 * FANOUT 调 FlowDispatchAction 算出拆分计划，按计划扇出 N 个子执行
 * （96 样本拆机场景）。由调度器 SubflowDriver 驱动，不配执行器。
 * 引用是否成环、目标是否 PUBLISHED 由 FlowGraphChecker 看全图后校验。
 */
@Component
public class SubflowStepType implements StepTypeDescriptor {

    /**
     * 扇出分发 Bean 白名单
     */
    private final FlowDispatchRegistry dispatchRegistry;

    public SubflowStepType(FlowDispatchRegistry dispatchRegistry) {
        this.dispatchRegistry = dispatchRegistry;
    }

    @Override
    public String type() {
        return FlowConstants.STEP_SUBFLOW;
    }

    @Override
    public StepRole role() {
        return StepRole.SUBFLOW;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.CHILD;
    }

    @Override
    public Class<?> configType() {
        return SubflowConfig.class;
    }

    @Override
    public boolean retryable() {
        // 子执行各自有独立生命周期，容器本身不重试
        return false;
    }

    /**
     * 发布期校验标量；目标存在性、PUBLISHED、引用链无环看全图，归 FlowGraphChecker
     */
    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof SubflowConfig subflow)) {
            issues.add(new FlowIssue(nodeId, "子流程节点缺少配置"));
            return;
        }
        String mode = StrUtil.blankToDefault(subflow.getDispatchMode(), "SINGLE");
        if ("SINGLE".equals(mode)) {
            if (StrUtil.isBlank(subflow.getPipelineKey())) {
                issues.add(new FlowIssue(nodeId, "子流程节点要写明引用的流程编码"));
            }
        } else if ("FANOUT".equals(mode)) {
            if (subflow.getDispatch() == null || !"bean".equals(subflow.getDispatch().getKind())
                    || StrUtil.isBlank(subflow.getDispatch().getBeanName())) {
                issues.add(new FlowIssue(nodeId, "扇出节点要配置分发 Bean"));
            } else if (!dispatchRegistry.isRegistered(subflow.getDispatch().getBeanName())) {
                issues.add(new FlowIssue(nodeId,
                        "扇出分发 Bean【" + subflow.getDispatch().getBeanName() + "】没有注册"));
            }
            String policy = StrUtil.blankToDefault(subflow.getCompletionPolicy(), "ALL_SUCCESS");
            if (!"ALL_SUCCESS".equals(policy) && !"ANY_SUCCESS".equals(policy)) {
                issues.add(new FlowIssue(nodeId, "扇出完成策略只能是 ALL_SUCCESS 或 ANY_SUCCESS"));
            }
        } else {
            issues.add(new FlowIssue(nodeId, "子流程的分发方式只能是 SINGLE 或 FANOUT"));
        }
    }

}

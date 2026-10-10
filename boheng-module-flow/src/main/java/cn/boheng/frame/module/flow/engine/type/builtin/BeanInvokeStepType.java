package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.engine.execution.ExecutionContextHolder;
import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.BeanInvokeConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * bean.invoke 调一个白名单 Spring Bean。与 FlowRouteAction / FlowDispatchAction
 * 同一套白名单思路：只有显式实现 FlowBeanAction 的 Bean 能被调用。
 */
@Component
public class BeanInvokeStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 全部可调用动作。SPI 允许暂时一个实现都没有（空名单也是合法状态），
     * 因此用 ObjectProvider 注入——集合直注入在零实现时容器会按
     * java.util.List 找 bean 并启动失败
     */
    @Autowired
    private ObjectProvider<FlowBeanAction> actionsProvider;

    /**
     * 上下文装载器。Bean 的第一个参数是完整执行上下文，
     * 与 FlowRouteAction / FlowDispatchAction 看到同一个视图
     */
    @Resource
    private ExecutionContextHolder contextHolder;

    /**
     * Bean 名 -> 实现
     */
    private final Map<String, FlowBeanAction> actionMap = new HashMap<>();

    /**
     * 启动时建索引
     */
    @PostConstruct
    public void init() {
        for (FlowBeanAction action : actionsProvider.orderedStream().toList()) {
            actionMap.put(action.beanName(), action);
        }
    }

    @Override
    public String type() {
        return FlowConstants.STEP_BEAN_INVOKE;
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
        return BeanInvokeConfig.class;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof BeanInvokeConfig bean)) {
            issues.add(new FlowIssue(nodeId, "Bean 节点缺少配置"));
            return;
        }
        if (StrUtil.isBlank(bean.getBeanName()) || !actionMap.containsKey(bean.getBeanName())) {
            issues.add(new FlowIssue(nodeId, "Bean【" + StrUtil.nullToEmpty(bean.getBeanName()) + "】没有注册为可调用动作"));
        }
    }

    /**
     * 执行：调白名单 Bean。第一个参数是完整执行上下文（input / sys / node 三层命名空间，
     * 外加 params = 本节点参数映射的解析结果），第二个参数是节点配置的固定参数。
     * Bean 抛异常按节点失败处理
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        BeanInvokeConfig config = ctx.config();
        FlowBeanAction action = actionMap.get(config.getBeanName());
        if (action == null) {
            return StepOutcome.failed("Bean 未注册: " + config.getBeanName());
        }
        Map<String, Object> args = StrUtil.isBlank(config.getArgsJson())
                ? Map.of() : JSONUtil.parseObj(config.getArgsJson());
        Map<String, Object> env = contextHolder.loadEnv(ctx.getExecutionId());
        env.put("params", ctx.getParams());
        try {
            String output = action.execute(env, args);
            return StepOutcome.success(output);
        } catch (RuntimeException ex) {
            return StepOutcome.failed(ex.getMessage());
        }
    }

}

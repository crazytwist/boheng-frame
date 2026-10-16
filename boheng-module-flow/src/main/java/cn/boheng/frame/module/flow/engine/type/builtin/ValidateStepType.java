package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.engine.execution.ExecutionContextHolder;
import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.ValidateConfig;
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
 * validate 校验门：调一个白名单 Bean，把「完整执行上下文」交给它判断，
 * 通过走 pass 出口，不通过走 fail 出口（不通过原因写进 node 输出供下游读）。
 *
 * <p>与 route 同属 CONTROL 角色、SYNC 完成方式，决策都落到 BRANCH；
 * 区别是校验动作拿到完整 env，且出口固定为 pass / fail 两个语义化分支。</p>
 */
@Component
public class ValidateStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 通过出口
     */
    public static final String BRANCH_PASS = "pass";

    /**
     * 不通过出口
     */
    public static final String BRANCH_FAIL = "fail";

    /**
     * 全部可调用校验动作。SPI 允许暂时零实现，用 ObjectProvider 注入
     * （集合直注入在零实现时容器会启动失败）
     */
    @Autowired
    private ObjectProvider<FlowValidateAction> actionsProvider;

    /**
     * 上下文装载器。校验拿到的是完整执行上下文（input / node / sys + params）
     */
    @Resource
    private ExecutionContextHolder contextHolder;

    /**
     * Bean 名 -> 实现
     */
    private final Map<String, FlowValidateAction> actionMap = new HashMap<>();

    /**
     * 启动时建索引
     */
    @PostConstruct
    public void init() {
        for (FlowValidateAction action : actionsProvider.orderedStream().toList()) {
            actionMap.put(action.beanName(), action);
        }
    }

    @Override
    public String type() {
        return FlowConstants.STEP_VALIDATE;
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
        return ValidateConfig.class;
    }

    @Override
    public boolean retryable() {
        // 校验是纯判断，失败重试没有意义（结果一样）
        return false;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof ValidateConfig validate)) {
            issues.add(new FlowIssue(nodeId, "校验节点缺少配置"));
            return;
        }
        if (StrUtil.isBlank(validate.getBeanName()) || !actionMap.containsKey(validate.getBeanName())) {
            issues.add(new FlowIssue(nodeId, "校验 Bean【" + StrUtil.nullToEmpty(validate.getBeanName())
                    + "】没有注册为可调用动作"));
        }
        if (validate.getBranches() == null || validate.getBranches().isEmpty()) {
            issues.add(new FlowIssue(nodeId, "校验节点必须配置 pass / fail 出口"));
            return;
        }
        checkBranch(validate, nodeId, BRANCH_PASS, issues);
        checkBranch(validate, nodeId, BRANCH_FAIL, issues);
    }

    private void checkBranch(ValidateConfig config, String nodeId, String key, List<FlowIssue> issues) {
        List<String> targets = config.getBranches().get(key);
        if (targets == null || targets.isEmpty()) {
            issues.add(new FlowIssue(nodeId, "校验节点缺少【" + key + "】出口"));
        }
    }

    /**
     * 执行：调校验 Bean，返回通过 / 不通过。不通过时把原因写进节点输出
     * （node.{nodeId}.reason），fail 出口下游可按参数映射读取。
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        ValidateConfig config = ctx.config();
        FlowValidateAction action = actionMap.get(config.getBeanName());
        if (action == null) {
            return StepOutcome.failed("校验 Bean 未注册: " + config.getBeanName());
        }
        Map<String, Object> args = StrUtil.isBlank(config.getArgsJson())
                ? Map.of() : JSONUtil.parseObj(config.getArgsJson());
        Map<String, Object> env = contextHolder.loadEnv(ctx.getExecutionId());
        env.put("params", ctx.getParams());
        String reason;
        try {
            reason = action.validate(env, args);
        } catch (RuntimeException ex) {
            reason = ex.getMessage();
        }
        if (StrUtil.isBlank(reason)) {
            return StepOutcome.branch(BRANCH_PASS);
        }
        return StepOutcome.branch(BRANCH_FAIL, JSONUtil.toJsonStr(Map.of("reason", reason)));
    }

}

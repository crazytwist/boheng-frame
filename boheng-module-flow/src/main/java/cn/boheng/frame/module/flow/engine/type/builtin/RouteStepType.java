package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.RouteMatchMode;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.expression.ExpressionEvaluator;
import cn.boheng.frame.module.flow.engine.route.FlowRouteAction;
import cn.boheng.frame.module.flow.engine.route.FlowRouteRegistry;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.RouteConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * route 出口路由（「拆分」的控制流形态）。
 *
 * <p>决策与执行分离：本执行器只算出一个出口 key 就返回（StepOutcome.BRANCH），
 * 拿 key 查 branches、激活目标子图、裁剪其余出口的下游，全部归调度器 BranchResolver。
 */
@Component
public class RouteStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 出口名规则：小写字母开头
     */
    private static final Pattern BRANCH_KEY = Pattern.compile(FlowConstants.BRANCH_KEY_REGEX);

    /**
     * 表达式求值器。四处共用同一个实现
     */
    private final ExpressionEvaluator evaluator;

    /**
     * Bean 决策器白名单
     */
    private final FlowRouteRegistry routeRegistry;

    public RouteStepType(ExpressionEvaluator evaluator, FlowRouteRegistry routeRegistry) {
        this.evaluator = evaluator;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public String type() {
        return FlowConstants.STEP_ROUTE;
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
        return RouteConfig.class;
    }

    @Override
    public boolean retryable() {
        // 路由是纯计算，失败没有重试意义（重试结果一样）
        return false;
    }

    /**
     * 发布期校验：出口名合法、default 必在、表达式可编译、Bean 在白名单、排他语义下目标不交叠
     */
    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof RouteConfig route)) {
            issues.add(new FlowIssue(nodeId, "路由节点缺少配置"));
            return;
        }
        if (route.getRouter() == null || StrUtil.isBlank(route.getRouter().getKind())) {
            issues.add(new FlowIssue(nodeId, "路由节点要选择决策方式（表达式或 Bean）"));
            return;
        }
        String kind = route.getRouter().getKind();
        if ("expression".equals(kind)) {
            if (StrUtil.isBlank(route.getRouter().getExpression())) {
                issues.add(new FlowIssue(nodeId, "路由节点要填写表达式"));
            } else {
                String error = evaluator.checkSyntax(route.getRouter().getExpression());
                if (error != null) {
                    issues.add(new FlowIssue(nodeId, "路由表达式不合法：" + error));
                }
            }
        } else if ("bean".equals(kind)) {
            String beanName = route.getRouter().getBeanName();
            if (StrUtil.isBlank(beanName) || !routeRegistry.isRegistered(beanName)) {
                issues.add(new FlowIssue(nodeId, "路由 Bean【" + StrUtil.nullToEmpty(beanName) + "】没有注册"));
            }
        } else {
            issues.add(new FlowIssue(nodeId, "路由决策方式只能是 expression 或 bean"));
        }
        if (route.getBranches() == null || route.getBranches().isEmpty()) {
            issues.add(new FlowIssue(nodeId, "路由节点至少要配一个出口"));
            return;
        }
        boolean hasDefault = false;
        for (Map.Entry<String, List<String>> entry : route.getBranches().entrySet()) {
            String key = entry.getKey();
            if (!BRANCH_KEY.matcher(key).matches()) {
                issues.add(new FlowIssue(nodeId, "出口名【" + key + "】须是小写字母开头，只含小写、数字和下划线"));
                continue;
            }
            if (FlowConstants.BRANCH_DEFAULT.equals(key)) {
                hasDefault = true;
            }
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                issues.add(new FlowIssue(nodeId, "出口【" + key + "】没有目标节点"));
            }
        }
        if (!hasDefault) {
            issues.add(new FlowIssue(nodeId, "路由节点必须配置 default 出口，兜底没有命中时走"));
        }
        String matchMode = StrUtil.blankToDefault(route.getMatchMode(), RouteMatchMode.EXCLUSIVE.name());
        if (RouteMatchMode.EXCLUSIVE.name().equals(matchMode) && route.getBranches().size() > 1) {
            // 排他语义下不同出口的目标不得交叠，否则一次激活会撞上同一节点
            Map<String, Integer> targetCount = new java.util.HashMap<>();
            route.getBranches().forEach((key, targets) -> {
                if (FlowConstants.BRANCH_DEFAULT.equals(key)) {
                    return; // default 与其它出口指向同一节点是合理的兜底
                }
                for (String target : targets == null ? List.<String>of() : targets) {
                    targetCount.merge(target, 1, Integer::sum);
                }
            });
            for (Map.Entry<String, Integer> entry : targetCount.entrySet()) {
                if (entry.getValue() > 1) {
                    issues.add(new FlowIssue(nodeId,
                            "排他路由的出口目标【" + entry.getKey() + "】被多个出口使用，会被重复激活"));
                }
            }
        }
    }

    /**
     * 执行：按决策方式算出出口 key 就返回。求值异常时走 default（发布期已保证存在）
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        RouteConfig config = ctx.config();
        String kind = config.getRouter().getKind();
        String key;
        if ("bean".equals(kind)) {
            FlowRouteAction action = routeRegistry.get(config.getRouter().getBeanName());
            if (action == null) {
                return StepOutcome.failed("路由 Bean 未注册: " + config.getRouter().getBeanName());
            }
            key = action.decide(ctx.getParams());
        } else {
            key = evaluator.evalString(config.getRouter().getExpression(), ctx.getParams());
        }
        if (StrUtil.isBlank(key) || !config.getBranches().containsKey(key)) {
            // 没命中或求值异常：走兜底出口
            key = FlowConstants.BRANCH_DEFAULT;
        }
        return StepOutcome.branch(key);
    }

    /**
     * 帮前端判断当前是否排他语义
     */
    public static boolean isExclusive(RouteConfig config) {
        return config == null || RouteMatchMode.EXCLUSIVE.name()
                .equals(StrUtil.blankToDefault(config.getMatchMode(), RouteMatchMode.EXCLUSIVE.name()));
    }

}

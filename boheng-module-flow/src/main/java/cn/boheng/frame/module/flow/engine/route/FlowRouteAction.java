package cn.boheng.frame.module.flow.engine.route;

import cn.boheng.frame.module.flow.enums.FlowConstants;

import java.util.Map;

/**
 * 路由决策器。业务在这里算出「走哪个出口」，只返回一个 key，
 * 不接触图、不写运行表、不 import 任何调度类——
 * 激活哪个节点、裁剪哪些节点全部归调度器 BranchResolver。
 */
public interface FlowRouteAction {

    /**
     * 命名空间说明：env 里有 input.xxx / node.xxx.yyy / sys.xxx，
     * 与 ExpressionEvaluator 一致
     *
     * @return 出口 key。必须能在 branches 里找到，或为 default
     */
    String decide(Map<String, Object> env);

    /**
     * Bean 名。默认取类名首字母小写，与 Spring 注入名对齐
     */
    default String beanName() {
        String simple = getClass().getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    /**
     * 便捷常量：兜底出口名
     */
    String DEFAULT = FlowConstants.BRANCH_DEFAULT;

}

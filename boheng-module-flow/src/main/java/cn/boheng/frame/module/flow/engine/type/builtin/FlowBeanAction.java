package cn.boheng.frame.module.flow.engine.type.builtin;

import java.util.Map;

/**
 * bean.invoke 可调用的动作。与 FlowRouteAction / FlowDispatchAction
 * 共用同一套白名单思路：只允许显式实现本接口的 Bean，
 * 流程里的 Bean 名不能变成任意代码执行入口。
 */
public interface FlowBeanAction {

    /**
     * 命名空间说明：env 里有 input.xxx / node.{nodeId}.{key} / sys.xxx 三层上下文，
     * 另有 params.{param}（本节点参数映射的解析结果）；args 是节点配置的固定参数
     *
     * @return 产出 JSON，写进上下文的 NODE 命名空间。无产出返回 null
     */
    String execute(Map<String, Object> env, Map<String, Object> args);

    /**
     * Bean 名。默认取类名首字母小写，与 Spring 注入名对齐
     */
    default String beanName() {
        String simple = getClass().getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

}

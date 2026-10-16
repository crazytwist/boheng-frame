package cn.boheng.frame.module.flow.engine.type.builtin;

import java.util.Map;

/**
 * validate 校验门的决策动作。与 FlowBeanAction / FlowRouteAction 同一套白名单思路：
 * 只有显式实现本接口的 Bean 能被校验节点调用，流程里的 Bean 名不能变成任意代码执行入口。
 *
 * <p>与 FlowRouteAction 的区别：校验动作拿到的是「完整执行上下文」（input / node / sys
 * 三层命名空间 + params），而路由动作只拿到本节点参数映射结果。校验通常需要综合
 * 上下游产出与系统变量一起判断，所以必须给完整上下文。</p>
 */
public interface FlowValidateAction {

    /**
     * 校验。返回 null 或空串表示「通过」（走 pass 出口）；
     * 返回非空字符串表示「不通过」，该字符串作为原因写进节点输出
     * （node.{nodeId}.reason），供 fail 出口下游读取。抛异常视为不通过（异常消息作为原因）。
     *
     * @param env  完整执行上下文（input.xxx / node.{nodeId}.{key} / sys.xxx，另有 params.{param}）
     * @param args 节点配置的固定参数（ValidateConfig.argsJson 解析结果）
     * @return null / 空串 = 通过；非空 = 不通过原因
     */
    String validate(Map<String, Object> env, Map<String, Object> args);

    /**
     * Bean 名。默认取类名首字母小写，与 Spring 注入名对齐
     */
    default String beanName() {
        String simple = getClass().getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

}

package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * route 路由节点的类型配置。
 *
 * <p>决策与执行分离：router 只算出一个 key（表达式或注册 Bean），
 * 拿 key 查 branches 找目标节点、激活与裁剪全部归调度器 BranchResolver。
 */
@Data
public class RouteConfig {

    /**
     * 决策器。kind = expression（画布可配）或 bean（注册白名单）
     */
    private Router router;

    /**
     * 出口映射：出口名 -&gt; 目标节点编码列表。必须包含 default 出口
     */
    private Map<String, List<String>> branches = new LinkedHashMap<>();

    /**
     * 匹配语义：EXCLUSIVE 排他（默认）。INCLUSIVE 仅模型保留，画布不提供入口
     */
    private String matchMode;

    /**
     * 没命中任何出口时：default 走兜底出口（默认），fail 判节点失败
     */
    private String missingKeyPolicy;

    /**
     * 决策器配置
     */
    @Data
    public static class Router {

        /**
         * expression 或 bean
         */
        private String kind;

        /**
         * kind=expression 时的表达式，如 value &gt; 3 ? 'high' : 'low'。
         * 求值变量来自上下文，详见 ExpressionEvaluator
         */
        private String expression;

        /**
         * kind=bean 时的注册 Bean 名，须实现 FlowRouteAction
         */
        private String beanName;

    }

}

package cn.boheng.frame.module.flow.enums;

/**
 * 路由节点的出口匹配语义。
 */
public enum RouteMatchMode {

    /**
     * 排他：一次只激活一个出口（BPMN 排他网关）。默认，画布只开放这个
     */
    EXCLUSIVE,

    /**
     * 包容：允许一次激活多个命中的出口（BPMN 包容网关）。仅模型保留，画布不提供入口
     */
    INCLUSIVE

}

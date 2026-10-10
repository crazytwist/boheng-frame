package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * script.run 的类型配置。一期只支持 aviator 表达式，
 * 求值结果写进上下文的 NODE 命名空间，供后续节点和路由读取。
 */
@Data
public class ScriptConfig {

    /**
     * 表达式内容，如 plateCount * 2
     */
    private String expression;

    /**
     * 结果写进上下文的键名。后续节点按 node.nodeId.键名 取值
     */
    private String resultKey;

}

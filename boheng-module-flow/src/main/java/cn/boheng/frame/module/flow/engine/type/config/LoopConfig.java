package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * loop 循环容器的类型配置。循环体是 bodyEntry 到 bodyExit 之间的一段子图，
 * 定义上没有回边——loop 节点在执行期接管 body 的迭代。
 */
@Data
public class LoopConfig {

    /**
     * 循环体入口节点编码
     */
    private String bodyEntryNodeId;

    /**
     * 循环体出口节点编码
     */
    private String bodyExitNodeId;

    /**
     * 轮次硬上限。必须填，防死循环
     */
    private Integer maxIterations;

    /**
     * 每轮结束后求值，true 就再来一轮。空则只跑 maxIterations 轮
     */
    private String whileExpression;

}

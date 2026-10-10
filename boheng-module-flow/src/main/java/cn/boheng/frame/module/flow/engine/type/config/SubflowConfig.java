package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * subflow 子流程的类型配置。
 *
 * <p>SINGLE：静态引用一个子流程，创建一个子执行；
 * FANOUT：调 FlowDispatchAction 算出拆分计划，按计划扇出 N 个子执行
 * （如 96 样本拆 48 去 A 线、48 去 B 线）。扇出点即汇合点。
 */
@Data
public class SubflowConfig {

    /**
     * SINGLE 单一子流程（默认），FANOUT 动态扇出
     */
    private String dispatchMode;

    /**
     * SINGLE：子流程编码
     */
    private String pipelineKey;

    /**
     * SINGLE：子流程版本。空则取该编码当前已发布的最新版
     */
    private Integer version;

    /**
     * SINGLE：传给子流程的入参 JSON
     */
    private String inputJson;

    /**
     * FANOUT：分发决策器。kind = bean（一期唯一形态）
     */
    private RouteConfig.Router dispatch;

    /**
     * FANOUT：完成策略。ALL_SUCCESS 全部成功才算成功（默认），
     * ANY_SUCCESS 任一成功即算成功
     */
    private String completionPolicy;

}

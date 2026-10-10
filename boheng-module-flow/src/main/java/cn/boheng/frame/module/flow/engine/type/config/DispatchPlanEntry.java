package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * FANOUT 拆分计划的一组。由 FlowDispatchAction 的实现产出，
 * 引擎按组创建子执行——Bean 只出计划，不启动执行。
 */
@Data
public class DispatchPlanEntry {

    /**
     * 这一组要跑的子流程编码
     */
    private String pipelineKey;

    /**
     * 子流程版本。空则取当前已发布的最新版
     */
    private Integer version;

    /**
     * 传给这组子流程的入参 JSON，如 {\"samples\":[...前48个]}
     */
    private String inputJson;

}

package cn.boheng.frame.module.flow.engine.type;

import java.util.Map;

/**
 * 一次节点执行的上下文。执行器从这里读输入，不直接查库。
 */
public final class StepExecutionContext {

    /**
     * 运行编号
     */
    private final Long executionId;

    /**
     * 节点编码
     */
    private final String nodeId;

    /**
     * 第几次尝试，从 1 开始
     */
    private final int attempt;

    /**
     * 循环轮次。非循环节点恒为 0
     */
    private final int iterationNo;

    /**
     * 解析好的输入参数。ParamResolver 按 INPUT / NODE / LITERAL 映射产出
     */
    private final Map<String, Object> params;

    /**
     * 类型专属配置（已按 Descriptor.configType() 反序列化）
     */
    private final Object config;

    /**
     * 等待毫秒数。节点级覆盖后生效值
     */
    private final long timeoutMs;

    public StepExecutionContext(Long executionId, String nodeId, int attempt, int iterationNo,
                                Map<String, Object> params, Object config, long timeoutMs) {
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.attempt = attempt;
        this.iterationNo = iterationNo;
        this.params = params;
        this.config = config;
        this.timeoutMs = timeoutMs;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getIterationNo() {
        return iterationNo;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 按目标类型取配置。类型对不上时抛 ClassCastException，属编程错误，直接暴露
     */
    @SuppressWarnings("unchecked")
    public <C> C config() {
        return (C) config;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

}

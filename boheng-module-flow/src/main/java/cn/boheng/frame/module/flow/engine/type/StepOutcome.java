package cn.boheng.frame.module.flow.engine.type;

import cn.hutool.core.util.StrUtil;

/**
 * 一个节点的执行结果。执行器只描述「这一步怎么样了」，
 * 推进图、重试、汇合判定全部归调度器，执行器不碰。
 */
public final class StepOutcome {

    /**
     * 结果种类
     */
    public enum Kind {

        /** 同步成功，产出在 outputJson */
        SUCCESS,

        /** 同步失败，可以按次数重试 */
        FAILED,

        /** 已下发，进入等待。等什么由 waitingFor 标记 */
        WAITING,

        /** 路由节点选出的出口 key。只由 route 类型返回 */
        BRANCH

    }

    /**
     * 结果种类
     */
    private final Kind kind;

    /**
     * 成功时的产出 JSON。写进上下文的 NODE 命名空间
     */
    private final String outputJson;

    /**
     * 等待什么：DEVICE_CALLBACK / DEVICE_COMMAND_CALLBACK / MANUAL / TIMER / CHILD
     */
    private final String waitingFor;

    /**
     * 等待的非密引用，如设备命令号。超时扫描按它找回来
     */
    private final String waitRef;

    /** 设备命令主键，等待设备命令时用于显式关联节点与命令。 */
    private final Long commandId;

    /** 设备命令号，便于审计和日志追踪。 */
    private final String commandNo;

    /**
     * 路由节点选出的出口 key
     */
    private final String branchKey;

    /**
     * 失败原因
     */
    private final String errorMessage;

    /**
     * 这次失败是否值得重试。null 表示没人声明过，按可重试处理（保持历史行为）。
     *
     * <p>由设备域按厂商协议给出事实（如容错能力声明里有没有 Retry），
     * 流程域据此决定要不要真的再试一次。</p>
     */
    private final Boolean retryable;

    private StepOutcome(Kind kind, String outputJson, String waitingFor, String waitRef,
                        Long commandId, String commandNo, String branchKey, String errorMessage,
                        Boolean retryable) {
        this.kind = kind;
        this.outputJson = outputJson;
        this.waitingFor = waitingFor;
        this.waitRef = waitRef;
        this.commandId = commandId;
        this.commandNo = commandNo;
        this.branchKey = branchKey;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }

    /**
     * 同步成功
     */
    public static StepOutcome success(String outputJson) {
        return new StepOutcome(Kind.SUCCESS, outputJson, null, null, null, null, null, null, null);
    }

    /**
     * 同步失败
     */
    public static StepOutcome failed(String errorMessage) {
        return failed(errorMessage, null);
    }

    /**
     * 同步失败，并说明这次失败值不值得重试。
     *
     * @param errorMessage 失败原因
     * @param retryable 设备域给出的可重试性；null 表示未声明，按可重试处理
     */
    public static StepOutcome failed(String errorMessage, Boolean retryable) {
        return new StepOutcome(Kind.FAILED, null, null, null, null, null, null, errorMessage, retryable);
    }

    /**
     * 已下发进入等待
     *
     * @param waitingFor 等什么
     * @param waitRef    非密引用
     */
    public static StepOutcome waiting(String waitingFor, String waitRef) {
        return waiting(waitingFor, waitRef, null, null);
    }

    /** 已下发进入等待，并显式携带设备命令关联信息。 */
    public static StepOutcome waiting(String waitingFor, String waitRef, Long commandId, String commandNo) {
        return new StepOutcome(Kind.WAITING, null, waitingFor, waitRef, commandId, commandNo, null, null, null);
    }

    /**
     * 已进入等待，并附带产出 JSON。
     * <p>等待类节点也有「要告诉外界的东西」：外部回调节点得先把带令牌的回调地址交出去，
     * 对方才有办法回调。产出落在 step_execution.output_data，运行详情直接可见，
     * 也写进 node.&lt;nodeId&gt; 上下文，后续节点可以引用。</p>
     */
    public static StepOutcome waitingWithOutput(String waitingFor, String waitRef, String outputJson) {
        return new StepOutcome(Kind.WAITING, StrUtil.trimToNull(outputJson),
                waitingFor, waitRef, null, null, null, null, null);
    }

    /**
     * 路由节点选出出口
     */
    public static StepOutcome branch(String branchKey) {
        return branch(branchKey, null);
    }

    /**
     * 路由 / 校验节点选出出口，并附带产出 JSON（如校验不通过的原因）。
     * 产出会写进上下文的 NODE 命名空间，供该出口下游读取。
     */
    public static StepOutcome branch(String branchKey, String outputJson) {
        return new StepOutcome(Kind.BRANCH, outputJson, null, null, null, null, branchKey, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public String getWaitingFor() {
        return waitingFor;
    }

    public String getWaitRef() {
        return waitRef;
    }

    public Long getCommandId() { return commandId; }

    public String getCommandNo() { return commandNo; }

    public String getBranchKey() {
        return branchKey;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 这次失败是否值得重试。没人声明过时按可重试处理。
     */
    public boolean isRetryable() {
        return !Boolean.FALSE.equals(retryable);
    }

    /**
     * 未加工的可重试性，null 表示未声明
     */
    public Boolean getRetryable() {
        return retryable;
    }

}

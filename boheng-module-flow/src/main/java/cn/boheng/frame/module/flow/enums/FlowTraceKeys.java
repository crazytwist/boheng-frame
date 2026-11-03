package cn.boheng.frame.module.flow.enums;

/**
 * Flow 跨模块追踪与幂等键约定。
 *
 * <p>一次流程节点动作在 Device / WMS 两侧使用同一前缀，便于按 execution/node/attempt
 * 检索全链路。调用方（Flow）负责生成；WMS 流水层在多明细时按 {@code key#index} 派生，
 * Device 侧使用不含 opType 的 {@link #sourceRef}。</p>
 *
 * <p>格式（写死，单测钉死；改格式等于破坏幂等）：
 * <ul>
 *   <li>sourceRef = {@code flow:{executionId}:{nodeId}:{iterationNo}:{attempt}}</li>
 *   <li>idempotentKey = {@code flow:{executionId}:{nodeId}:{iterationNo}:{attempt}:{opType}}</li>
 *   <li>movement 行键 = {@code {idempotentKey}#{index}}（WMS applyTrace 派生，调用方勿自带 #）</li>
 * </ul>
 *
 * <p>同一 attempt 重试必须复用同一 idempotentKey；WMS 撞 {@code uk_idempotent} 时应回放首次结果，
 * 不得当错误抛出导致调用方换新键再扣一次。换 attempt 才生成新键。</p>
 *
 * @see cn.boheng.frame.module.flow.engine.type.device.DeviceCommandStepType
 */
public final class FlowTraceKeys {

    private FlowTraceKeys() {
    }

    /** 键前缀：标识来源为流程引擎 */
    public static final String PREFIX = "flow";

    // ==================== WMS 操作类型（idempotentKey 末段） ====================
    // 与 MaterialApi / SampleApi 契约对齐；本期不含 putIn / takeOut / transfer。

    /** 可用性检查（只读，一般可不落流水；若落流水仍用此 opType） */
    public static final String OP_CHECK = "check";

    /** 轻量占用 / 使用开始 */
    public static final String OP_USE = "use";

    /** 按物料实例消耗 */
    public static final String OP_CONSUME = "consume";

    /** 按容器消耗 */
    public static final String OP_CONSUME_BY_CONTAINER = "consumeByContainer";

    /** 释放占用 */
    public static final String OP_RELEASE = "release";

    /** 样本登记（SampleApi） */
    public static final String OP_REGISTER_SAMPLE = "registerSample";

    /**
     * Device 侧业务键（sourceRef）。
     *
     * @param executionId 流程执行 ID
     * @param nodeId      节点编码
     * @param iterationNo 循环轮次；非循环节点为 0
     * @param attempt     尝试次数，从 1 开始
     */
    public static String sourceRef(long executionId, String nodeId, int iterationNo, int attempt) {
        return PREFIX + ":" + executionId + ":" + nodeId + ":" + iterationNo + ":" + attempt;
    }

    /**
     * WMS 调用方幂等键。同一节点同一 attempt 同一 opType 必须稳定复用本键。
     *
     * @param opType 见 {@code OP_*} 常量
     */
    public static String idempotentKey(long executionId, String nodeId, int iterationNo, int attempt,
                                       String opType) {
        return sourceRef(executionId, nodeId, iterationNo, attempt) + ":" + opType;
    }

    /**
     * WMS 内部流水行键派生规则（与 {@code MaterialInstanceServiceImpl#applyTrace} 一致）。
     * Flow 调用方传入的仍是不带 {@code #index} 的 {@link #idempotentKey}；本方法供对照与测试钉死。
     *
     * @param idempotentKey 调用方业务键
     * @param index         同一次请求内的流水序号，从 0 起
     */
    public static String movementIdempotentKey(String idempotentKey, int index) {
        return idempotentKey + "#" + index;
    }

}

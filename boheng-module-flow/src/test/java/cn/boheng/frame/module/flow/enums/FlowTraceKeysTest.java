package cn.boheng.frame.module.flow.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 追踪/幂等键格式回归。格式一变，Device 业务键与 WMS uk_idempotent 回放全部失效，必须钉死。
 */
class FlowTraceKeysTest {

    /**
     * sourceRef：flow:{executionId}:{nodeId}:{iterationNo}:{attempt}
     */
    @Test
    void sourceRefFormat() {
        assertEquals("flow:1001:consume_reagent:0:1",
                FlowTraceKeys.sourceRef(1001L, "consume_reagent", 0, 1));
    }

    /**
     * idempotentKey = sourceRef + :{opType}
     */
    @Test
    void idempotentKeyAppendsOpType() {
        assertEquals("flow:1001:consume_reagent:0:1:consume",
                FlowTraceKeys.idempotentKey(1001L, "consume_reagent", 0, 1, FlowTraceKeys.OP_CONSUME));
        assertEquals(
                FlowTraceKeys.sourceRef(1001L, "consume_reagent", 0, 1) + ":" + FlowTraceKeys.OP_CHECK,
                FlowTraceKeys.idempotentKey(1001L, "consume_reagent", 0, 1, FlowTraceKeys.OP_CHECK));
    }

    /**
     * 六种本期 opType 字面量钉死，避免魔法串漂移
     */
    @Test
    void opTypeLiteralsPinned() {
        assertEquals("check", FlowTraceKeys.OP_CHECK);
        assertEquals("use", FlowTraceKeys.OP_USE);
        assertEquals("consume", FlowTraceKeys.OP_CONSUME);
        assertEquals("consumeByContainer", FlowTraceKeys.OP_CONSUME_BY_CONTAINER);
        assertEquals("release", FlowTraceKeys.OP_RELEASE);
        assertEquals("registerSample", FlowTraceKeys.OP_REGISTER_SAMPLE);
    }

    /**
     * 同 attempt 不同 opType → 不同键（检查与消耗互不覆盖）
     */
    @Test
    void differentOpTypesDifferentKeys() {
        String consume = FlowTraceKeys.idempotentKey(9L, "n1", 0, 1, FlowTraceKeys.OP_CONSUME);
        String release = FlowTraceKeys.idempotentKey(9L, "n1", 0, 1, FlowTraceKeys.OP_RELEASE);
        assertNotEquals(consume, release);
    }

    /**
     * 换 attempt 必须换键——允许新一次业务扣减；同 attempt 重试则键不变
     */
    @Test
    void attemptDistinguishesRetries() {
        String a1 = FlowTraceKeys.idempotentKey(9L, "n1", 0, 1, FlowTraceKeys.OP_CONSUME);
        String a2 = FlowTraceKeys.idempotentKey(9L, "n1", 0, 2, FlowTraceKeys.OP_CONSUME);
        assertNotEquals(a1, a2);
        assertEquals(a1, FlowTraceKeys.idempotentKey(9L, "n1", 0, 1, FlowTraceKeys.OP_CONSUME));
    }

    /**
     * 循环轮次进入键：同节点不同 iteration 互不覆盖
     */
    @Test
    void iterationNoInKey() {
        assertEquals("flow:5:loop_body:2:1:use",
                FlowTraceKeys.idempotentKey(5L, "loop_body", 2, 1, FlowTraceKeys.OP_USE));
        assertNotEquals(
                FlowTraceKeys.idempotentKey(5L, "loop_body", 1, 1, FlowTraceKeys.OP_USE),
                FlowTraceKeys.idempotentKey(5L, "loop_body", 2, 1, FlowTraceKeys.OP_USE));
    }

    /**
     * WMS applyTrace 派生：{idempotentKey}#{index}；调用方键本身不含 #
     */
    @Test
    void movementKeyDerivation() {
        String base = FlowTraceKeys.idempotentKey(7L, "put", 0, 1, FlowTraceKeys.OP_CONSUME_BY_CONTAINER);
        assertEquals(base + "#0", FlowTraceKeys.movementIdempotentKey(base, 0));
        assertEquals(base + "#1", FlowTraceKeys.movementIdempotentKey(base, 1));
        assertTrue(base.indexOf('#') < 0);
    }

}

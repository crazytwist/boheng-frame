package cn.boheng.frame.module.flow.engine.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 执行引擎纯函数测试：FANOUT 子运行的业务键生成规则。
 *
 * <p>业务键是 Java 侧幂等建行的判据，格式固定为 {@code 父运行:父节点:尝试:组号}；
 * 数据库侧再由 flow_execution.business_key_guard 生成列 + uk_flow_execution_fanout
 * 唯一索引兜底。这里把最容易改坏的拼接规则钉住——格式一变，幂等就失效。
 */
class FlowExecutionEngineTest {

    /**
     * 业务键格式：父运行:父节点:尝试:组号
     */
    @Test
    void fanoutBusinessKeyFormat() {
        assertEquals("2108834011929083906:heavy_dispatch:1:0",
                FlowExecutionEngine.fanoutBusinessKey(2108834011929083906L, "heavy_dispatch", 1, 0));
    }

    /**
     * 同一父节点同一尝试下，组号是区分粒度——组号不同键不同
     */
    @Test
    void groupIndexDistinguishesFanout() {
        assertEquals("1:dispatch:1:0", FlowExecutionEngine.fanoutBusinessKey(1L, "dispatch", 1, 0));
        assertEquals("1:dispatch:1:1", FlowExecutionEngine.fanoutBusinessKey(1L, "dispatch", 1, 1));
        assertNotEquals(
                FlowExecutionEngine.fanoutBusinessKey(1L, "dispatch", 1, 0),
                FlowExecutionEngine.fanoutBusinessKey(1L, "dispatch", 1, 1));
    }

    /**
     * 不同尝试不同键——重试后的扇出与首轮扇出互不覆盖
     */
    @Test
    void attemptDistinguishesFanout() {
        assertNotEquals(
                FlowExecutionEngine.fanoutBusinessKey(1L, "dispatch", 1, 0),
                FlowExecutionEngine.fanoutBusinessKey(1L, "dispatch", 2, 0));
    }

}

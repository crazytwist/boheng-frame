package cn.boheng.frame.module.flow.engine.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 步骤结果的可重试性语义。
 *
 * <p>设备域只在「设备明确说了不该重试」时给 false；没人声明的失败必须保持可重试，
 * 否则 Bean 调用、脚本、校验这些不走设备域的失败会突然不再重试。</p>
 */
class StepOutcomeTest {

    /**
     * 未声明时按可重试处理，保持历史行为。
     */
    @Test
    void shouldBeRetryableWhenNobodyDeclares() {
        StepOutcome outcome = StepOutcome.failed("设备无响应");

        assertTrue(outcome.isRetryable());
        assertNull(outcome.getRetryable());
    }

    /**
     * 设备声明不可重试时，流程应停止重试。
     */
    @Test
    void shouldNotRetryWhenDeviceSaysNo() {
        StepOutcome outcome = StepOutcome.failed("机械臂SDK错误", Boolean.FALSE);

        assertFalse(outcome.isRetryable());
        assertEquals(Boolean.FALSE, outcome.getRetryable());
    }

    /**
     * 设备声明可重试时，保持重试。
     */
    @Test
    void shouldRetryWhenDeviceSaysYes() {
        assertTrue(StepOutcome.failed("托盘未就位", Boolean.TRUE).isRetryable());
    }

    /**
     * 成功与分支不参与重试判断。
     */
    @Test
    void shouldKeepSuccessUntouched() {
        assertTrue(StepOutcome.success("{}").isRetryable());
        assertTrue(StepOutcome.branch("ok").isRetryable());
    }

}

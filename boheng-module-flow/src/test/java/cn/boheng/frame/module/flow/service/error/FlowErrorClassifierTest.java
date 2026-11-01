package cn.boheng.frame.module.flow.service.error;

import cn.boheng.frame.module.flow.enums.error.FlowErrorCategory;
import cn.boheng.frame.module.flow.enums.error.FlowErrorRetryMode;
import cn.boheng.frame.module.flow.enums.error.FlowErrorSourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowErrorClassifierTest {

    @Test
    void classifyValidation() {
        var c = FlowErrorClassifier.classifyStepFailure("参数不合法：缺少 sampleId");
        assertEquals(FlowErrorSourceType.FLOW_STEP, c.sourceType());
        assertEquals(FlowErrorCategory.VALIDATION, c.category());
        assertEquals(FlowErrorRetryMode.NEVER, c.retryMode());
        assertEquals("FLOW_STEP_VALIDATION", c.errorCode());
    }

    @Test
    void classifyWmsBusiness() {
        var c = FlowErrorClassifier.classifyStepFailure("库存不足，样本不允许消耗");
        assertEquals(FlowErrorSourceType.WMS, c.sourceType());
        assertEquals(FlowErrorCategory.BUSINESS_RULE, c.category());
        assertEquals("FLOW_WMS_BUSINESS", c.errorCode());
    }

    @Test
    void classifyDependencyTimeout() {
        var c = FlowErrorClassifier.classifyStepFailure("WMS connection timeout");
        assertEquals(FlowErrorSourceType.SYSTEM, c.sourceType());
        assertEquals(FlowErrorCategory.DEPENDENCY, c.category());
        assertEquals(FlowErrorRetryMode.IDEMPOTENT, c.retryMode());
    }

    @Test
    void classifyInternalBug() {
        var c = FlowErrorClassifier.classifyStepFailure("NullPointerException at line 12");
        assertEquals(FlowErrorSourceType.FLOW_ENGINE, c.sourceType());
        assertEquals(FlowErrorCategory.INTERNAL_BUG, c.category());
        assertEquals(FlowErrorRetryMode.NEVER, c.retryMode());
    }

    @Test
    void classifyDefault() {
        var c = FlowErrorClassifier.classifyStepFailure("something went wrong");
        assertEquals(FlowErrorSourceType.FLOW_STEP, c.sourceType());
        assertEquals(FlowErrorCategory.UNKNOWN, c.category());
        assertEquals("FLOW_STEP_FAILED", c.errorCode());
    }
}

package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.execution.ExecutionContextHolder;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.builtin.BeanInvokeStepType;
import cn.boheng.frame.module.flow.engine.type.config.BeanInvokeConfig;
import cn.boheng.frame.module.wms.api.SampleApi;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 Bean 动作使用当前节点尝试，而不是执行级 sys 的旧值。 */
class WaterRegisterTraceTest {

    @Test
    void registrationKeyUsesCurrentIterationAndAttempt() {
        SampleApi sampleApi = mock(SampleApi.class);
        when(sampleApi.registerSamples(anyList())).thenReturn(List.of("TASK-001"));
        WaterRegisterAction action = new WaterRegisterAction();
        ReflectionTestUtils.setField(action, "sampleApi", sampleApi);

        ExecutionContextHolder holder = mock(ExecutionContextHolder.class);
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put("input", Map.of("taskNo", "TASK", "sampleCount", 1));
        persisted.put("sys", Map.of("executionId", 9001L, "iterationNo", 0, "attempt", 1));
        persisted.put("node", Map.of());
        when(holder.loadEnv(9001L)).thenReturn(persisted);

        BeanInvokeStepType executor = new BeanInvokeStepType();
        ReflectionTestUtils.setField(executor, "contextHolder", holder);
        @SuppressWarnings("unchecked")
        Map<String, Object> actions = (Map<String, Object>) ReflectionTestUtils.getField(executor, "actionMap");
        actions.put(action.beanName(), action);
        BeanInvokeConfig config = new BeanInvokeConfig();
        config.setBeanName(action.beanName());

        String first = run(executor, config, 2, 1);
        assertEquals("flow:9001:register:2:1:registerSample", first);
        assertEquals(first, run(executor, config, 2, 1));
        assertEquals("flow:9001:register:3:1:registerSample", run(executor, config, 3, 1));
        assertFalse(persisted.containsKey("step"));
    }

    @Test
    void missingStepContextFailsInsteadOfUsingFirstAttempt() {
        WaterRegisterAction action = new WaterRegisterAction();
        assertThrows(IllegalStateException.class, () -> action.execute(
                Map.of("input", Map.of("taskNo", "TASK", "sampleCount", 1)), Map.of()));
    }

    private static String run(BeanInvokeStepType executor, BeanInvokeConfig config, int iteration, int attempt) {
        StepExecutionContext context = new StepExecutionContext(9001L, "register", attempt, iteration,
                Map.of(), config, 1000L);
        StepOutcome outcome = executor.execute(context);
        assertEquals(StepOutcome.Kind.SUCCESS, outcome.getKind());
        return JSONUtil.parseObj(outcome.getOutputJson()).getStr("idempotentKey");
    }
}

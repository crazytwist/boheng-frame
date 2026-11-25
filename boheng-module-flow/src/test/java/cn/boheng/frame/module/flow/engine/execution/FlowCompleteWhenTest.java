package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.engine.expression.ExpressionEvaluator;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点完成断言：判定收在流程侧，节点用 run_config.completeWhen 表达
 * 「这一次算不算完成」。这里钉住三件事——配置能读出来、表达式能取到
 * output/params 两个作用域、写错变量时绝不放行。
 */
class FlowCompleteWhenTest {

    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    /**
     * run_config 里的 completeWhen 要能被运行期视图读到
     */
    @Test
    void runtimeNodeReadsCompleteWhen() {
        FlowNodeDO row = new FlowNodeDO();
        row.setNodeId("weigh");
        row.setRunConfig("{\"timeoutMs\":600000,\"completeWhen\":\"output.weight >= 0.95\"}");
        assertEquals("output.weight >= 0.95", RuntimeNode.of(row).runCompleteWhen());
    }

    /**
     * 没配断言的节点保持原行为：执行器报成功即成功
     */
    @Test
    void missingCompleteWhenMeansNoAssertion() {
        FlowNodeDO row = new FlowNodeDO();
        row.setNodeId("weigh");
        row.setRunConfig("{\"timeoutMs\":1000}");
        assertNull(RuntimeNode.of(row).runCompleteWhen());
    }

    /**
     * 断言同时能取本节点产出和本次入参：公差常常来自参数
     */
    @Test
    void assertionSeesOutputAndParams() {
        Map<String, Object> env = new HashMap<>();
        env.put("output", JSONUtil.parseObj("{\"weight\":0.98}"));
        env.put("params", JSONUtil.parseObj("{\"targetWeight\":0.95}"));
        assertTrue(evaluator.evalBool("output.weight >= params.targetWeight", env));
    }

    /**
     * 超出公差区间 → 不成立，引擎据此降级为失败
     */
    @Test
    void outOfToleranceDoesNotPass() {
        Map<String, Object> env = new HashMap<>();
        env.put("output", JSONUtil.parseObj("{\"weight\":1.20}"));
        assertFalse(evaluator.evalBool("output.weight >= 0.95 && output.weight <= 1.05", env));
    }

    /**
     * 变量名写错不能静默放行：无论是求值异常还是返 false，结论都必须是「不通过」
     */
    @Test
    void undefinedVariableNeverPasses() {
        Map<String, Object> env = new HashMap<>();
        env.put("output", JSONUtil.parseObj("{\"weight\":0.98}"));
        boolean passed;
        try {
            passed = evaluator.evalBool("wight >= 0.95", env);
        } catch (RuntimeException ex) {
            // 求值异常按不通过处理，正是引擎 evaluateCompleteWhen 的策略
            passed = false;
        }
        assertFalse(passed);
    }

}

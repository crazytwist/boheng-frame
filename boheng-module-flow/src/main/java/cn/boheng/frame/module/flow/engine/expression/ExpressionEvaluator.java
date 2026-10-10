package cn.boheng.frame.module.flow.engine.expression;

import cn.boheng.frame.module.flow.enums.FlowConstants;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 表达式求值器。路由 router.expression、loop.whileExpression、
 * script.run、notification 内容占位四处共用同一个实现，
 * 变量命名空间也一致：input.xxx（启动入参）、node.xxx.yyy（节点输出）、sys.xxx（系统变量）。
 */
@Component
public class ExpressionEvaluator {

    /**
     * 编译期校验。发布时调用，表达式写错当场拒绝，不等运行期才炸
     *
     * @return 错误说明，null 表示合法
     */
    public String checkSyntax(String expression) {
        if (expression == null || expression.isBlank()) {
            return "表达式不能为空";
        }
        try {
            AviatorEvaluator.compile(expression);
            return null;
        } catch (ExpressionSyntaxErrorException ex) {
            return ex.getMessage();
        }
    }

    /**
     * 求值为布尔。loop.whileExpression 用
     */
    public boolean evalBool(String expression, Map<String, Object> env) {
        Object value = eval(expression, env);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 求值为字符串。route router.expression 用，结果就是出口 key
     */
    public String evalString(String expression, Map<String, Object> env) {
        Object value = eval(expression, env);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 通用求值。script.run 用，结果写进上下文
     */
    public Object eval(String expression, Map<String, Object> env) {
        Expression compiled = AviatorEvaluator.compile(expression);
        return compiled.execute(env);
    }

    /**
     * 求值并带回当时的变量快照，供审计落 flow_branch_decision.decision_data
     */
    public EvalResult evalWithSnapshot(String expression, Map<String, Object> env) {
        Object value = eval(expression, env);
        return new EvalResult(value, env);
    }

    /**
     * 一次求值的结果与变量快照
     */
    public record EvalResult(Object value, Map<String, Object> snapshot) {
    }

}

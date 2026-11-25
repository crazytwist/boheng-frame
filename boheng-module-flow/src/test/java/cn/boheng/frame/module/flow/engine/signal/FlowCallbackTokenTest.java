package cn.boheng.frame.module.flow.engine.signal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点级外部回调的令牌：同一执行同一节点同一次尝试恒定可重算，
 * 换尝试即换令牌——重试后旧回调必须打不进来。
 */
class FlowCallbackTokenTest {

    /**
     * 构造一个带密钥的令牌工具。secret 由 @Value 注入，单测用反射补上
     */
    private static FlowCallbackToken tokenWithSecret(String secret) {
        FlowCallbackToken token = new FlowCallbackToken();
        try {
            Field field = FlowCallbackToken.class.getDeclaredField("secret");
            field.setAccessible(true);
            field.set(token, secret);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return token;
    }

    @Test
    void issueIsStableAndRecomputable() {
        FlowCallbackToken token = tokenWithSecret("secret-a");
        String first = token.issue(100L, "step_a", 1);
        assertEquals(first, token.issue(100L, "step_a", 1));
        assertEquals(32, first.length());
        assertTrue(token.matches(100L, "step_a", 1, first));
    }

    @Test
    void retryGetsDifferentToken() {
        FlowCallbackToken token = tokenWithSecret("secret-a");
        assertNotEquals(token.issue(100L, "step_a", 1), token.issue(100L, "step_a", 2));
        // 第一次尝试的令牌不能用来结算第二次尝试
        assertFalse(token.matches(100L, "step_a", 2, token.issue(100L, "step_a", 1)));
    }

    @Test
    void differentNodeOrSecretDoesNotMatch() {
        FlowCallbackToken a = tokenWithSecret("secret-a");
        FlowCallbackToken b = tokenWithSecret("secret-b");
        String token = a.issue(100L, "step_a", 1);
        assertFalse(a.matches(100L, "step_b", 1, token));
        assertFalse(b.matches(100L, "step_a", 1, token));
    }

    @Test
    void blankInputNeverMatches() {
        FlowCallbackToken token = tokenWithSecret("secret-a");
        assertFalse(token.matches(100L, "step_a", 1, null));
        assertFalse(token.matches(100L, "step_a", 1, "   "));
        assertFalse(token.matches(null, "step_a", 1, token.issue(100L, "step_a", 1)));
        assertFalse(token.matches(100L, "step_a", null, token.issue(100L, "step_a", 1)));
    }

}

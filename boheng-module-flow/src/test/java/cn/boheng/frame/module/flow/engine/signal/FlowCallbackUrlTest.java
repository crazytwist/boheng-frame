package cn.boheng.frame.module.flow.engine.signal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点回调地址：必须带 token，且这个 token 能通过服务端校验。
 *
 * <p>防的是这么一种回归：令牌改成派生式以后，只保留了「校验」而忘了「下发」，
 * 对方知道该打哪个路径却永远过不了校验，表现为回调永远 409、节点永远超时。
 */
class FlowCallbackUrlTest {

    /**
     * 拼一个指定根地址的回调地址工具。secret / base 都由 @Value、Environment 注入，单测手动补
     */
    private static FlowCallbackUrl urlWith(String base, String secret) {
        FlowCallbackToken token = new FlowCallbackToken();
        setField(token, "secret", secret);
        FlowCallbackUrl url = new FlowCallbackUrl();
        MockEnvironment env = new MockEnvironment();
        if (base != null) {
            env.setProperty(FlowCallbackUrl.CALLBACK_BASE_PROPERTY, base);
        }
        setField(url, "environment", env);
        setField(url, "callbackToken", token);
        return url;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void urlCarriesEverythingTheCounterpartyNeeds() {
        String url = urlWith("http://10.0.0.5:48080", "secret-a")
                .callbackUrl(9001L, "wait_lims", 2);
        assertTrue(url.startsWith("http://10.0.0.5:48080/app-api/flow/external-callback/9001/wait_lims?"),
                url);
        assertTrue(url.contains("attempt=2"), url);
        assertTrue(url.contains("token="), url);
    }

    @Test
    void issuedTokenPassesServerSideCheck() {
        FlowCallbackToken token = new FlowCallbackToken();
        setField(token, "secret", "secret-a");
        FlowCallbackUrl url = urlWith("http://10.0.0.5:48080", "secret-a");
        setField(url, "callbackToken", token);

        String issued = url.callbackUrl(9001L, "wait_lims", 1);
        String raw = issued.substring(issued.indexOf("token=") + "token=".length());
        // 这条断言就是整个链路的关键：对方拿到的 token 必须能被 dispatch 侧认
        assertTrue(token.matches(9001L, "wait_lims", 1, raw), issued);
        // 换个尝试次数就不认了，避免旧回调结算新一轮
        assertFalse(token.matches(9001L, "wait_lims", 2, raw));
    }

    @Test
    void baseWithoutSchemePathNoiseIsTrimmed() {
        // 末尾多写斜杠不该生成 //app-api
        String url = urlWith("http://10.0.0.5:48080/", "secret-a").callbackUrl(1L, "n", 1);
        assertTrue(url.startsWith("http://10.0.0.5:48080/app-api/"), url);
        assertEquals("http://10.0.0.5:48080/app-api", urlWith("http://10.0.0.5:48080/", "s").base());
    }

    @Test
    void missingBaseFallsBackToLocal() {
        // 未配置时退回本机，只用于本机联调；显式断言这个回落，免得以后改成静默失败
        String base = urlWith(null, "secret-a").base();
        assertTrue(base.startsWith("http://127.0.0.1:"), base);
        assertTrue(base.endsWith("/app-api"), base);
    }

}

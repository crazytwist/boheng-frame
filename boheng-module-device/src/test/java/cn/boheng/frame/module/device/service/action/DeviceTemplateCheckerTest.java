package cn.boheng.frame.module.device.service.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报文映射保存期校验的单元测试。
 */
class DeviceTemplateCheckerTest {

    /**
     * 被测校验器。
     */
    private final DeviceTemplateChecker checker = new DeviceTemplateChecker();

    /**
     * HPLC 转动动作的输入契约。
     */
    private static final String SCHEMA = "{\"TrayNumber\":{\"type\":\"INTEGER\",\"required\":true}}";

    /**
     * 声明过的参数与内置 UUID 函数都应放行。
     */
    @Test
    void shouldAllowDeclaredParamAndUuidFunction() {
        assertDoesNotThrow(() -> checker.check(SCHEMA,
                "{\"TrayNumber\":${TrayNumber},\"EventId\":\"${uuid()}\",\"RequestId\":\"${uuid()}\"}"));
    }

    /**
     * 网关注入的系统变量不属于输入契约，但模板里允许引用。
     */
    @Test
    void shouldAllowSystemVariables() {
        assertDoesNotThrow(() -> checker.check(null,
                "{\"no\":\"${commandNo}\",\"cb\":\"${callbackUrl}\",\"token\":\"${callbackToken}\"}"));
    }

    /**
     * 点路径参数按顶层名判定，与运行期取值口径一致。
     */
    @Test
    void shouldAllowNestedParamPath() {
        assertDoesNotThrow(() -> checker.check(
                "{\"sample.id\":{\"type\":\"STRING\"}}", "{\"id\":\"${sample.id}\"}"));
    }

    /**
     * 契约没声明的占位符必须在保存时就拦下，而不是等到下发。
     */
    @Test
    void shouldRejectUndeclaredPlaceholder() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checker.check(SCHEMA, "{\"WaveLengthNM\":${wavelength}}"));

        assertTrue(error.getMessage().contains("wavelength"));
    }

    /**
     * 未实现的内置函数同样要在保存期拒绝，避免运行期报未解析占位符。
     */
    @Test
    void shouldRejectUnknownFunction() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checker.check(SCHEMA, "{\"t\":\"${now()}\"}"));

        assertTrue(error.getMessage().contains("now"));
    }

    /**
     * 没有模板的动作用于纯文本或空报文，直接放行。
     */
    @Test
    void shouldSkipBlankTemplate() {
        assertDoesNotThrow(() -> checker.check(SCHEMA, null));
        assertDoesNotThrow(() -> checker.check(SCHEMA, "   "));
    }

}

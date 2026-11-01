package cn.boheng.frame.module.device.service.action;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备动作参数解析器单元测试。
 */
class DeviceParamResolverTest {

    /** 被测参数解析器。 */
    private final DeviceParamResolver resolver = new DeviceParamResolver();

    /**
     * 参数定义默认值应补入最终命令参数，显式输入保持最高优先级。
     */
    @Test
    void shouldApplyDefaultsAndKeepExplicitValues() {
        String schema = """
                {
                  "wavelength":{"type":"INTEGER","required":true,"default":450,"constraints":{"min":200,"max":1000}},
                  "mode":{"type":"ENUM","default":"ABSORBANCE","constraints":["ABSORBANCE","FLUORESCENCE"]},
                  "shake":{"type":"BOOLEAN","default":false}
                }
                """;

        JSONObject result = JSONUtil.parseObj(resolver.resolve(schema, "{\"wavelength\":485}"));

        assertEquals(485L, result.getLong("wavelength"));
        assertEquals("ABSORBANCE", result.getStr("mode"));
        assertEquals(false, result.getBool("shake"));
    }

    /**
     * 缺少必填参数时必须拒绝下发。
     */
    @Test
    void shouldRejectMissingRequiredValue() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> resolver.resolve("{\"sample.id\":{\"type\":\"STRING\",\"required\":true}}", "{}"));

        assertTrue(error.getMessage().contains("sample.id"));
    }

    /**
     * 未定义参数不能静默进入厂商报文。
     */
    @Test
    void shouldRejectUnknownValue() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> resolver.resolve("{\"wavelength\":{\"type\":\"INTEGER\"}}", "{\"waveLength\":450}"));

        assertTrue(error.getMessage().contains("waveLength"));
    }

    /**
     * 数值范围与枚举约束必须在后端执行。
     */
    @Test
    void shouldRejectConstraintViolation() {
        assertThrows(RuntimeException.class,
                () -> resolver.resolve("{\"wavelength\":{\"type\":\"INTEGER\",\"constraints\":{\"min\":200,\"max\":1000}}}",
                        "{\"wavelength\":1200}"));
        assertThrows(RuntimeException.class,
                () -> resolver.resolve("{\"mode\":{\"type\":\"ENUM\",\"constraints\":[\"A\",\"B\"]}}",
                        "{\"mode\":\"C\"}"));
    }

    /**
     * 历史字符串简写参数定义应继续可用，避免已有动作阻断参数集创建。
     */
    @Test
    void shouldSupportLegacyStringDefinition() {
        JSONObject result = JSONUtil.parseObj(resolver.resolve(
                "{\"workMode\":\"STRING\",\"manualOperateCommand\":\"手动操作命令\"}",
                "{\"workMode\":\"1\",\"manualOperateCommand\":\"1\"}"));

        assertEquals("1", result.getStr("workMode"));
        assertEquals("1", result.getStr("manualOperateCommand"));
    }

    /**
     * 历史数字默认值简写不应改变参数集显式提交的 JSON 类型。
     */
    @Test
    void shouldSupportLegacyNumericDefaultDefinition() {
        JSONObject result = JSONUtil.parseObj(resolver.resolve(
                "{\"workMode\":1,\"manualOperateCommand\":1}",
                "{\"workMode\":\"1\",\"manualOperateCommand\":\"1\"}"));

        assertEquals("1", result.getStr("workMode"));
        assertEquals("1", result.getStr("manualOperateCommand"));
    }
}

package cn.boheng.frame.module.device.service.action;

import cn.boheng.frame.module.device.service.simulate.SimulateMessageRenderer;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HPLC（S6000PLUS）转动指令的参数解析与报文渲染回归测试。
 *
 * <p>取自厂商给出的真实报文样例：TrayNumber 是唯一的业务参数，
 * EventId / RequestId 每次下发都要新的 UUID。</p>
 */
class HplcRotateCommandTest {

    /**
     * 动作输入契约：托盘号 1-9。
     */
    private static final String PARAM_SCHEMA = """
            {"TrayNumber":{"label":"托盘号","type":"INTEGER","required":true,"default":1,
             "constraints":{"min":1,"max":9}}}""";

    /**
     * 动作报文模板。EventId / RequestId 就是普通字段，
     * 各用一次 ${uuid()} 现填，天然不重复，不需要平台另外处理。
     */
    private static final String REQUEST_TEMPLATE = """
            {"Command":"Rotate","CommandSyncAsync":0,"DeviceId":"S6000PLUS",
             "EventId":"${uuid()}","Parameters":{"TrayNumber":${TrayNumber}},
             "RequestId":"${uuid()}","RunMode":0}""";

    /**
     * 被测参数解析器。
     */
    private final DeviceParamResolver resolver = new DeviceParamResolver();

    /**
     * 渲染出的报文必须是合法 JSON，托盘号是数字，两个 UUID 各不相同。
     */
    @Test
    void shouldRenderRotateCommandWithFreshIds() {
        String params = resolver.resolve(PARAM_SCHEMA, "{\"TrayNumber\":3}");
        String body = SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params));

        JSONObject command = JSONUtil.parseObj(body);
        assertEquals("Rotate", command.getStr("Command"));
        assertEquals(0, command.getInt("CommandSyncAsync"));
        assertEquals(0, command.getInt("RunMode"));

        // 不加引号的占位符必须还原成数字，否则设备会按字符串拒收
        Object tray = command.getJSONObject("Parameters").get("TrayNumber");
        assertInstanceOf(Number.class, tray);
        assertEquals(3, ((Number) tray).intValue());

        String eventId = command.getStr("EventId");
        String requestId = command.getStr("RequestId");
        assertTrue(eventId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertTrue(requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertNotEquals(eventId, requestId);
        assertFalse(body.contains("${"));
    }

    /**
     * 同一个模板连渲染两次：两次各自拿一套新的 UUID，两处 UUID 又互不相同。
     * 这就是 EventId / RequestId 的全部要求 —— 不重复。
     */
    @Test
    void shouldGenerateFreshIdsOnEveryRender() {
        String params = resolver.resolve(PARAM_SCHEMA, "{\"TrayNumber\":1}");
        JSONObject first = JSONUtil.parseObj(SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params)));
        JSONObject second = JSONUtil.parseObj(SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params)));

        assertNotEquals(first.getStr("EventId"), second.getStr("EventId"));
        assertNotEquals(first.getStr("RequestId"), second.getStr("RequestId"));
        assertNotEquals(first.getStr("EventId"), first.getStr("RequestId"));
    }

    /**
     * 不传托盘号时按契约默认值补 1。
     */
    @Test
    void shouldFallBackToDefaultTrayNumber() {
        String params = resolver.resolve(PARAM_SCHEMA, "{}");
        String body = SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params));

        assertEquals(1, JSONUtil.parseObj(body).getJSONObject("Parameters").getInt("TrayNumber"));
    }

    /**
     * 超出 1-9 的托盘号在平台侧就拒绝，不要发给设备。
     */
    @Test
    void shouldRejectTrayNumberOutOfRange() {
        assertThrows(RuntimeException.class, () -> resolver.resolve(PARAM_SCHEMA, "{\"TrayNumber\":12}"));
        assertThrows(RuntimeException.class, () -> resolver.resolve(PARAM_SCHEMA, "{\"TrayNumber\":0}"));
    }

    /**
     * 字符串参数带引号会撑破报文结构：模板是纯文本替换，不做转义。
     * 这类报文必须由网关的渲染后校验拦下，不能发给设备。
     *
     * <p>这里顺带锁住一个坑：JSONUtil.isTypeJSON 只看首尾字符，对坏包也返回 true，
     * 校验必须用真正的解析。</p>
     */
    @Test
    void shouldProduceUnparsableJsonWhenStringValueCarriesQuote() {
        String schema = "{\"BatchNo\":{\"type\":\"STRING\"}}";
        String template = "{\"BatchNo\":\"${BatchNo}\"}";
        String params = resolver.resolve(schema, "{\"BatchNo\":\"A\\\"B\"}");
        String body = SimulateMessageRenderer.render(template, JSONUtil.parseObj(params));

        assertThrows(RuntimeException.class, () -> JSONUtil.parse(body.trim()));
    }

}

package cn.boheng.frame.module.device.service.action;

import cn.boheng.frame.module.device.service.simulate.SimulateMessageRenderer;
import cn.boheng.frame.module.device.service.template.MessageTemplateFunctions;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AGV 生成任务单（genAgvSchedulingTask）的输入契约与报文渲染回归测试。
 *
 * <p>厂商确认的报文体只有 4 个字段，取值方式各不相同：</p>
 * <ul>
 *   <li>agvCode —— 固定车号，写死在模板里</li>
 *   <li>reqCode —— 每次随机的 16 位大写十六进制，用内置函数生成</li>
 *   <li>taskTyp —— 业务输入，唯一进契约的参数</li>
 *   <li>wbCode  —— 固定目标工位，写死在模板里</li>
 * </ul>
 *
 * <p>与 sql/mysql/device-agv-migrate.sql 保持一致。</p>
 */
class AgvTaskCommandTest {

    /**
     * 动作输入契约：只有 taskTyp 是业务输入。
     *
     * <p>固定值和流水号都不进契约 —— 前者是协议知识，后者由平台生成。
     * 调用方硬传 reqCode 会被 rejectUnknownParams 拒掉，避免两条任务共用流水号。</p>
     */
    private static final String PARAM_SCHEMA = """
            {"taskTyp":{"label":"任务类型","type":"STRING","required":true}}""";

    /**
     * 动作报文模板。
     */
    private static final String REQUEST_TEMPLATE = """
            {"agvCode":"1900","reqCode":"${randomHex(16)}","taskTyp":"${taskTyp}","wbCode":"006800BB031000"}""";

    /**
     * 被测参数解析器。
     */
    private final DeviceParamResolver resolver = new DeviceParamResolver();

    /**
     * 报文体就是这 4 个字段，固定值原样落地，没有残留占位符。
     */
    @Test
    void shouldRenderFourFieldBody() {
        String params = resolver.resolve(PARAM_SCHEMA, "{\"taskTyp\":\"Task01\"}");
        String body = SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params));

        JSONObject task = JSONUtil.parseObj(body);
        assertEquals(4, task.size());
        assertEquals("1900", task.getStr("agvCode"));
        assertEquals("006800BB031000", task.getStr("wbCode"));
        assertEquals("Task01", task.getStr("taskTyp"));
        assertFalse(body.contains("${"));
    }

    /**
     * reqCode 必须是 16 位大写十六进制 —— 与厂商样例 8FADAE4C08FD0046 一致。
     */
    @Test
    void shouldRenderReqCodeAsUpperCaseHex() {
        String params = resolver.resolve(PARAM_SCHEMA, "{\"taskTyp\":\"Task01\"}");
        String body = SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params));

        String reqCode = JSONUtil.parseObj(body).getStr("reqCode");
        assertEquals(16, reqCode.length());
        assertTrue(reqCode.matches("[0-9A-F]{16}"), "reqCode 格式不对：" + reqCode);
    }

    /**
     * 连发两次，reqCode 必须不同，否则 AGV 会把第二条当重复任务丢掉。
     */
    @Test
    void shouldGenerateFreshReqCodeOnEveryRender() {
        String params = resolver.resolve(PARAM_SCHEMA, "{\"taskTyp\":\"Task01\"}");
        String first = JSONUtil.parseObj(
                SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params))).getStr("reqCode");
        String second = JSONUtil.parseObj(
                SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params))).getStr("reqCode");

        assertNotEquals(first, second);
    }

    /**
     * taskTyp 是唯一由调用方决定的字段，必须原样进报文。
     */
    @Test
    void shouldPassCallerSuppliedTaskTyp() {
        String params = resolver.resolve(PARAM_SCHEMA, "{\"taskTyp\":\"Task04\"}");
        String body = SimulateMessageRenderer.render(REQUEST_TEMPLATE, JSONUtil.parseObj(params));

        assertEquals("Task04", JSONUtil.parseObj(body).getStr("taskTyp"));
    }

    /**
     * taskTyp 没有默认值，不传就该在平台侧拦下，别发个空任务类型给设备。
     */
    @Test
    void shouldRejectMissingTaskTyp() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> resolver.resolve(PARAM_SCHEMA, "{}"));

        assertTrue(ex.getMessage().contains("taskTyp"), "应当指出缺少 taskTyp：" + ex.getMessage());
    }

    /**
     * reqCode 由平台生成，调用方硬传要被拒 —— 否则会出现两条任务共用一个流水号。
     */
    @Test
    void shouldRejectCallerSuppliedReqCode() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resolver.resolve(PARAM_SCHEMA, "{\"taskTyp\":\"Task01\",\"reqCode\":\"8FADAE4C08FD0046\"}"));

        assertTrue(ex.getMessage().contains("reqCode"), "应当明确说是 reqCode 未定义：" + ex.getMessage());
    }

    /**
     * randomHex 的位数可控，省略时按 16 位，越界夹到上限，非数字按默认。
     */
    @Test
    void shouldHonourRandomHexLength() {
        assertTrue(MessageTemplateFunctions.apply("${randomHex()}").matches("[0-9A-F]{16}"));
        assertTrue(MessageTemplateFunctions.apply("${randomHex(8)}").matches("[0-9A-F]{8}"));
        assertTrue(MessageTemplateFunctions.apply("${randomHex(32)}").matches("[0-9A-F]{32}"));
        assertTrue(MessageTemplateFunctions.apply("${ randomhex( 12 ) }").matches("[0-9A-F]{12}"));
        // 非法位数回落到默认值，不能抛异常把整条报文打掉
        assertTrue(MessageTemplateFunctions.apply("${randomHex(abc)}").matches("[0-9A-F]{16}"));
    }

    /**
     * 不认识的函数不能被静默吃掉，否则报文里会留一个空值。
     */
    @Test
    void shouldKeepUnknownFunctionAsIs() {
        assertEquals("${now()}", MessageTemplateFunctions.apply("${now()}"));
        assertFalse(MessageTemplateFunctions.isSupportedFunction("now"));
        assertTrue(MessageTemplateFunctions.isSupportedFunction("RANDOMHEX"));
    }

    /**
     * 模板引用了契约里没有的占位符时，保存期就要拦住。
     */
    @Test
    void shouldRejectTemplatePlaceholderOutsideSchema() {
        DeviceTemplateChecker checker = new DeviceTemplateChecker();

        assertThrows(RuntimeException.class,
                () -> checker.check(PARAM_SCHEMA, "{\"foo\":\"${foo}\"}"));
        // 内置函数和系统变量不算未声明
        checker.check(PARAM_SCHEMA, "{\"reqCode\":\"${randomHex(16)}\",\"no\":\"${commandNo}\"}");
    }

}

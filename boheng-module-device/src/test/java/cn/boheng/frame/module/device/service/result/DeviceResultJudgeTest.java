package cn.boheng.frame.module.device.service.result;

import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static cn.boheng.frame.module.device.enums.DeviceEnums.ErrorTypeEnum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备应答判定器单元测试。用例取自 HPLC 厂商协议 7.1.2 / 7.1.3 的真实报文。
 */
class DeviceResultJudgeTest {

    /**
     * 被测判定器。
     */
    private final DeviceResultJudge judge = new DeviceResultJudge();

    /**
     * 厂商协议给出的判定规则。
     */
    private static final String RULE = """
            {"successStatusPath":"$.ExecuteStatus","successStatusValues":[0],
             "successCodePath":"$.ResultCode","successCodeValues":["00000000"],
             "messagePath":"$.Message","retryHintsPath":"$.Parameters.HandleCommands"}""";

    private DeviceActionDO action() {
        DeviceActionDO action = new DeviceActionDO();
        action.setActionCode("trun");
        action.setResultRule(RULE);
        return action;
    }

    /**
     * 7.1.2 的成功应答。
     */
    @Test
    void shouldAcceptSuccessResponse() {
        String body = """
                {"ExecuteStatus":0,"ResultCode":"00000000","Message":"成功",
                 "RequestId":"85a57212-b0cf-43bf-a660-8354e5346dcd","Command":"Rotate","Parameters":{},"Data":null}""";

        ResultVerdict verdict = judge.judge(action(), body, 200);

        assertTrue(verdict.success());
        assertNull(verdict.errorType());
        assertNull(verdict.retryable());
    }

    /**
     * 7.1.3 的失败回调，设备声明支持重试。
     */
    @Test
    void shouldRejectFailedCallbackWithRetryHint() {
        String body = """
                {"ErrorType":0,"DeviceErrorAffect":0,"ExecuteStatus":1,"ResultCode":"10008023",
                 "Message":"机械臂SDK错误","Command":"Rotate",
                 "Parameters":{"HandleCommands":["Retry","Ignore"]}}""";

        ResultVerdict verdict = judge.judge(action(), body, 200);

        assertFalse(verdict.success());
        assertEquals(ErrorTypeEnum.DEVICE_REJECTED.getType(), verdict.errorType());
        assertEquals(Boolean.TRUE, verdict.retryable());
        assertTrue(verdict.message().contains("机械臂SDK错误"));
        assertTrue(verdict.message().contains("10008023"));
    }

    /**
     * 设备只声明 Ignore，说明这条指令不该重试。
     */
    @Test
    void shouldMarkNotRetryableWhenOnlyIgnoreDeclared() {
        String body = """
                {"ExecuteStatus":1,"ResultCode":"10008024","Message":"托盘未就位",
                 "Parameters":{"HandleCommands":["Ignore"]}}""";

        ResultVerdict verdict = judge.judge(action(), body, 200);

        assertFalse(verdict.success());
        assertEquals(Boolean.FALSE, verdict.retryable());
    }

    /**
     * 没声明容错能力时不下结论，交给流程按默认处理。
     */
    @Test
    void shouldLeaveRetryableEmptyWithoutHints() {
        String body = "{\"ExecuteStatus\":1,\"ResultCode\":\"10008025\",\"Message\":\"未知错误\"}";

        ResultVerdict verdict = judge.judge(action(), body, 200);

        assertFalse(verdict.success());
        assertNull(verdict.retryable());
    }

    /**
     * 执行状态成功但结果码不对，也算失败（两个条件是且的关系）。
     */
    @Test
    void shouldRejectWhenResultCodeMismatches() {
        ResultVerdict verdict = judge.judge(action(),
                "{\"ExecuteStatus\":0,\"ResultCode\":\"10008023\"}", 200);

        assertFalse(verdict.success());
    }

    /**
     * 没配规则时按 ExecuteStatus 兜底，避免失败回调被当成成功。
     */
    @Test
    void shouldFallBackToExecuteStatusWithoutRule() {
        DeviceActionDO noRule = new DeviceActionDO();
        noRule.setActionCode("trun");

        assertFalse(judge.judge(noRule, "{\"ExecuteStatus\":1}", 200).success());
        assertTrue(judge.judge(noRule, "{\"ExecuteStatus\":0}", 200).success());
    }

    /**
     * HTTP 层失败归传输，可重试。
     */
    @Test
    void shouldClassifyHttpFailureAsTransport() {
        ResultVerdict verdict = judge.judge(action(), "Bad Gateway", 502);

        assertFalse(verdict.success());
        assertEquals(ErrorTypeEnum.TRANSPORT.getType(), verdict.errorType());
        assertEquals(Boolean.TRUE, verdict.retryable());
    }

    /**
     * 纯文本响应交给解析规则，不在这里判成败。
     */
    @Test
    void shouldAcceptNonJsonBody() {
        assertTrue(judge.judge(action(), "OK", 200).success());
        assertTrue(judge.judge(action(), null, 200).success());
    }

    /**
     * 规则要求的结果码字段不在报文里时跳过该条件，不能判成失败。
     *
     * <p>受理应答和最终回调常常不是同一个结构：AGV 的受理应答有 {@code code}，
     * 最终回调由设备自己发，未必带这个字段。按失败处理会把成功的回调判死。</p>
     */
    @Test
    void shouldSkipConditionWhenRuleFieldAbsent() {
        DeviceActionDO agv = new DeviceActionDO();
        agv.setActionCode("genAgvSchedulingTask");
        agv.setResultRule("{\"successCodePath\":\"$.code\",\"successCodeValues\":[\"0\"],\"messagePath\":\"$.message\"}");

        // 受理应答：code=0 受理
        assertTrue(judge.judge(agv, "{\"code\":0,\"data\":\"Task01_X\",\"message\":\"成功\"}", 200).success());
        // 受理应答：code=1 拒绝
        assertFalse(judge.judge(agv, "{\"code\":1,\"message\":\"任务已满\"}", 200).success());
        // 最终回调没有 code 字段：判不了成功，也不能判成失败
        assertTrue(judge.judge(agv, "{\"data\":\"Task01_X\",\"status\":\"DONE\"}", 200).success());
    }

    /**
     * 反复收到「没有 ack 字段」的回调，判定结论不受影响。
     *
     * <p>缺字段提示做了进程内去重（同一动作同一路径只 WARN 一次），
     * 去重只作用于日志，不能影响判定结果——否则第二条回调开始就会改成失败。</p>
     */
    @Test
    void shouldJudgeEveryCallbackEvenAfterMissingFieldWarned() {
        DeviceActionDO agv = new DeviceActionDO();
        agv.setActionCode("genAgvSchedulingTask");
        agv.setResultRule("{\"successCodePath\":\"$.code\",\"successCodeValues\":[\"0\"]}");

        String callback = "{\"data\":\"Task01_X\",\"method\":\"end\"}";
        for (int i = 0; i < 5; i++) {
            assertTrue(judge.judge(agv, callback, 200).success(), "第 " + (i + 1) + " 条回调仍应判成功");
        }
        // 去重后仍然认真正的拒绝
        assertFalse(judge.judge(agv, "{\"code\":1,\"message\":\"任务已满\"}", 200).success());
    }

    /**
     * 缺字段的 WARN 同一动作只打一次：回调几十条也只提示一行，不刷屏。
     */
    @Test
    void shouldWarnOnlyOnceWhenRuleFieldAbsent() {
        Logger logger = (Logger) LoggerFactory.getLogger(DeviceResultJudge.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DeviceActionDO agv = new DeviceActionDO();
            agv.setDeviceTypeCode("AGV");
            agv.setActionCode("genAgvSchedulingTask");
            agv.setResultRule("{\"successCodePath\":\"$.code\",\"successCodeValues\":[\"0\"]}");
            String callback = "{\"data\":\"Task01_X\",\"method\":\"end\"}";
            for (int i = 0; i < 5; i++) {
                assertTrue(judge.judge(agv, callback, 200).success());
            }
        } finally {
            logger.detachAppender(appender);
        }
        long warns = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().contains("$.code"))
                .count();
        assertEquals(1L, warns, "同一动作同一缺失字段只应提示一次");
    }

    /**
     * 数字与字符串写法互通：规则里写 "0"，报文里是 0 也算命中
     */
    @Test
    void shouldCompareNumberAndTextEqually() {
        DeviceActionDO agv = new DeviceActionDO();
        agv.setResultRule("{\"successCodePath\":\"$.code\",\"successCodeValues\":[\"0\"]}");

        assertTrue(judge.judge(agv, "{\"code\":0}", 200).success());
        assertTrue(judge.judge(agv, "{\"code\":\"0\"}", 200).success());
        assertFalse(judge.judge(agv, "{\"code\":2}", 200).success());
    }

    /**
     * 终态判断：只有 method=end 才算任务结束，中间回调不能结算。
     */
    @Test
    void shouldOnlySettleOnConfiguredTerminalValue() {
        DeviceActionDO agv = new DeviceActionDO();
        agv.setResultRule("{\"settlePath\":\"$.method\",\"settleValues\":[\"end\"]}");

        assertTrue(judge.isTerminal(agv, "{\"method\":\"end\",\"taskCode\":\"T1\"}"));
        assertFalse(judge.isTerminal(agv, "{\"method\":\"start\",\"taskCode\":\"T1\"}"));
        assertFalse(judge.isTerminal(agv, "{\"method\":\"move\",\"taskCode\":\"T1\"}"));
    }

    /**
     * 没配终态规则、报文里没有该字段、正文不是 JSON —— 都按终态处理。
     *
     * <p>设备域可能配了「回调不超时」，这里卡住就是永久挂起，所以宁可结算。</p>
     */
    @Test
    void shouldTreatUnjudgeableCallbackAsTerminal() {
        DeviceActionDO noRule = new DeviceActionDO();
        assertTrue(judge.isTerminal(noRule, "{\"method\":\"start\"}"));
        assertTrue(judge.isTerminal(null, "{\"method\":\"start\"}"));

        DeviceActionDO agv = new DeviceActionDO();
        agv.setResultRule("{\"settlePath\":\"$.method\",\"settleValues\":[\"end\"]}");
        assertTrue(judge.isTerminal(agv, "{\"taskCode\":\"T1\"}"), "没有 method 字段时不能卡住");
        assertTrue(judge.isTerminal(agv, "OK"), "非 JSON 正文不能卡住");
        assertTrue(judge.isTerminal(agv, null));
    }

    /**
     * 配了 settlePath 但没配 settleValues 等于没配，别把回调全卡住
     */
    @Test
    void shouldIgnoreSettlePathWithoutValues() {
        DeviceActionDO agv = new DeviceActionDO();
        agv.setResultRule("{\"settlePath\":\"$.method\"}");

        assertTrue(judge.isTerminal(agv, "{\"method\":\"start\"}"));
    }

}

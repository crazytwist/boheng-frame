package cn.boheng.frame.module.device.service.callback;

import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.result.DeviceResultJudge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定地址回调按关联键认命令的单元测试。
 *
 * <p>盯三件事：</p>
 * <ul>
 *   <li>报文里没有命令号时怎么认出是哪条命令（值可能藏在顶层也可能埋在嵌套里）</li>
 *   <li>设备回的是「另一个等价字段」时（HPLC 的 EventId / RequestId 只回一个）能不能认出来</li>
 *   <li>认不出来要明确回答 404 让厂商重发，命中但已经结束过要回答 409 而不是再结算一次</li>
 * </ul>
 */
class DeviceCorrelateCallbackTest {

    /**
     * 还在等回调的命令
     */
    private final List<DeviceCommandDO> waiting = new ArrayList<>();

    /**
     * 结算调用记录
     */
    private final List<Long> completed = new ArrayList<>();

    /**
     * 下一次结算是否被认（false 表示命令已结束、抢不到状态）
     */
    private boolean completeAccepted = true;

    /**
     * 被测服务
     */
    private DeviceCallbackService service;

    /**
     * 当前命令对应的动作定义，为空表示没配判定规则（收到即结算）
     */
    private DeviceActionDO action;

    @BeforeEach
    void setUp() {
        waiting.clear();
        completed.clear();
        completeAccepted = true;
        action = null;
        service = new DeviceCallbackService();
        ReflectionTestUtils.setField(service, "deviceCommandMapper", mapper());
        ReflectionTestUtils.setField(service, "deviceCommandService", commandService());
        ReflectionTestUtils.setField(service, "deviceActionMapper", actionMapper());
        ReflectionTestUtils.setField(service, "deviceResultJudge", new DeviceResultJudge());
    }

    /**
     * 关联键在顶层：AGV 的报文就是这种形态。
     */
    @Test
    void shouldMatchTopLevelCorrelateValue() {        waiting.add(command(1L, "C0001", "AC170558B944CBBE",
                "{\"reqCode\":\"AC170558B944CBBE\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"code\":0,\"reqCode\":\"AC170558B944CBBE\",\"data\":\"Task01_1\"}", "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals("C0001", receipt.commandNo());
        assertEquals(List.of(1L), completed);
    }

    /**
     * 关联键埋在嵌套对象里也要认得出来 —— 不按字段名挑，按值比。
     */
    @Test
    void shouldMatchNestedCorrelateValue() {
        waiting.add(command(2L, "C0002", "8FADAE4C08FD0046",
                "{\"reqCode\":\"8FADAE4C08FD0046\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"code\":0,\"Parameters\":{\"reqCode\":\"8FADAE4C08FD0046\"}}", "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals(List.of(2L), completed);
    }

    /**
     * 设备回的是另一个等价字段：关联键列存的是 EventId，回调只回了 RequestId。
     *
     * <p>HPLC 就是这种：请求里 EventId 和 RequestId 都是现生成的，回调回哪个不一定。
     * 关联键列只有一个位置，另一个只能靠报文内容兜底匹配。</p>
     */
    @Test
    void shouldFallBackToPayloadMatch() {
        waiting.add(command(3L, "C0003", "9a282078-b6cd-4130-878c-524d4c84986a",
                "{\"EventId\":\"9a282078-b6cd-4130-878c-524d4c84986a\","
                        + "\"RequestId\":\"08c58345-c213-43dd-a050-f41c450cfd19\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"ExecuteStatus\":0,\"RequestId\":\"08c58345-c213-43dd-a050-f41c450cfd19\"}",
                "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals("C0003", receipt.commandNo());
        assertEquals(List.of(3L), completed);
    }

    /**
     * 精确匹配优先：关联键对得上就不该再去扫报文。
     */
    @Test
    void shouldPreferCorrelateValueOverPayloadScan() {
        waiting.add(command(4L, "C0004", "AAAA", "{\"reqCode\":\"AAAA\"}"));
        waiting.add(command(5L, "C0005", "BBBB", "{\"note\":\"AAAA 只是正文里出现的\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate("{\"reqCode\":\"AAAA\"}", "application/json");

        assertEquals("C0004", receipt.commandNo());
        assertEquals(List.of(4L), completed);
    }

    /**
     * 一个都对不上：回答 404，厂商据此重发。
     */
    @Test
    void shouldReturn404WhenNothingMatches() {
        waiting.add(command(6L, "C0006", "AAAA", "{\"reqCode\":\"AAAA\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate("{\"reqCode\":\"BBBB\"}", "application/json");

        assertEquals(404, receipt.httpStatus());
        assertTrue(completed.isEmpty(), "没匹配到就不该结算任何命令");
    }

    /**
     * 命中了但命令已经结束（并发重发或刚超时收口）：回答 409，不能再结算一次。
     */
    @Test
    void shouldReturn409WhenCommandAlreadySettled() {
        waiting.add(command(7L, "C0007", "AC170558B944CBBE", "{\"reqCode\":\"AC170558B944CBBE\"}"));
        completeAccepted = false;

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"reqCode\":\"AC170558B944CBBE\"}", "application/json");

        assertEquals(409, receipt.httpStatus());
        assertEquals("C0007", receipt.commandNo());
    }

    /**
     * 报文为空或不是 JSON：拿不出任何候选值，直接 400。
     */
    @Test
    void shouldReturn400WhenBodyHasNothingToMatch() {
        assertEquals(400, service.receiveByCorrelate(null, "application/json").httpStatus());
        assertEquals(400, service.receiveByCorrelate("  ", "application/json").httpStatus());
        assertEquals(400, service.receiveByCorrelate("not json", "text/plain").httpStatus());
        assertEquals(400, service.receiveByCorrelate("{}", "application/json").httpStatus());
        assertEquals(400, service.receiveByCorrelate("{\"data\":null}", "application/json").httpStatus());
    }

    /**
     * 超长字符串（多半是正文内容而不是关联键）不参与比对，免得白扫一遍库。
     *
     * <p>全被滤掉之后等于「报文里没有可用的关联字段」，所以这里回 400 而不是 404：
     * 404 留给「值看着像关联键、但库里没有对应的等待命令」。</p>
     */
    @Test
    void shouldIgnoreOverlongValues() {
        String longText = "x".repeat(200);
        waiting.add(command(8L, "C0008", longText, "{\"data\":\"" + longText + "\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"data\":\"" + longText + "\"}", "application/json");

        assertEquals(400, receipt.httpStatus());
        assertTrue(completed.isEmpty(), "超长值不该匹配到任何命令");
    }

    /**
     * 带 LIKE 通配符的值不能进兜底匹配，否则 % 会把所有等待命令都扫出来。
     */
    @Test
    void shouldNotUseWildcardInPayloadFallback() {
        waiting.add(command(9L, "C0009", "whatever", "{\"note\":\"abc\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate("{\"note\":\"%\"}", "application/json");

        assertEquals(404, receipt.httpStatus());
        assertTrue(completed.isEmpty());
    }

    /**
     * 短值不参与兜底匹配。
     *
     * <p>回调报文里到处是 {@code ExecuteStatus:0}、{@code code:0} 这种单个字符，
     * 拿 "0" 去 LIKE 报文内容，UUID 里全是数字，一撞就中——必须按长度挡掉。</p>
     */
    @Test
    void shouldNotMatchByShortValueInPayloadFallback() {
        waiting.add(command(10L, "C0010", "9a282078-b6cd-4130-878c-524d4c84986a",
                "{\"EventId\":\"9a282078-b6cd-4130-878c-524d4c84986a\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"IsAccepted\":true,\"ExecuteStatus\":0,\"State\":0}", "application/json");

        assertEquals(404, receipt.httpStatus(), "单字符值不该命中任何命令");
        assertTrue(completed.isEmpty());
    }

    /**
     * AGV 的一问两答：第二答的真实报文没有 code / message，只有 method=end。
     *
     * <p>这条报文按关联键 reqCode 能认出来，整条链路应当结算成功。</p>
     */
    @Test
    void shouldSettleAgvSecondAnswerByReqCode() {
        action = agvAction("{\"successCodePath\":\"$.code\",\"successCodeValues\":[\"0\"],"
                + "\"settlePath\":\"$.method\",\"settleValues\":[\"end\"]}");
        waiting.add(command(11L, "C0011", "8FADAE4C08FD0046", "{\"reqCode\":\"8FADAE4C08FD0046\"}"));

        String body = """
                {"reqCode":"8FADAE4C08FD0046","reqTime":"2019-04-03 10:08:06","cooX":"3000","cooY":"21999",
                 "currentPositionCode":"p02","mapCode":"AA","method":"end","podCode":"100001","podDir":"0",
                 "robotCode":"6001","taskCode":"test169E0F39740116Q_ECS","wbCode":"p02"}""";

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(body, "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals("ok", receipt.message());
        assertEquals(List.of(11L), completed);
    }

    /**
     * 配了终态判断后，中途的回调（method 不是 end）只能确认收到，不能结算。
     *
     * <p>提前结算的后果很实在：流程会带着「车已经到站」的结论往下走，
     * 接着给一台还在路上的车派下一个任务，而真正的结束报文只会收到 409。</p>
     */
    @Test
    void shouldNotSettleBeforeTerminalMethod() {
        action = agvAction("{\"settlePath\":\"$.method\",\"settleValues\":[\"end\"]}");
        waiting.add(command(12L, "C0012", "1541954B96B1112", "{\"reqCode\":\"1541954B96B1112\"}"));

        DeviceCallbackReceipt running = service.receiveByCorrelate(
                "{\"reqCode\":\"1541954B96B1112\",\"method\":\"start\",\"taskCode\":\"T1\"}", "application/json");

        assertEquals(200, running.httpStatus(), "要回 200，否则设备会一直重发");
        assertTrue(running.message().contains("尚未结束"));
        assertTrue(completed.isEmpty(), "没到终态不该结算");

        DeviceCallbackReceipt done = service.receiveByCorrelate(
                "{\"reqCode\":\"1541954B96B1112\",\"method\":\"end\",\"taskCode\":\"T1\"}", "application/json");

        assertEquals(200, done.httpStatus());
        assertEquals(List.of(12L), completed);
    }

    /**
     * 配了终态判断、但报文里根本没有这个字段时按终态处理：不能因为配置不全把回调卡死
     * （设备域可能配了「回调不超时」，卡住就是永久挂起）。
     */
    @Test
    void shouldSettleWhenTerminalFieldAbsent() {
        action = agvAction("{\"settlePath\":\"$.method\",\"settleValues\":[\"end\"]}");
        waiting.add(command(13L, "C0013", "AC170558B944CBBE", "{\"reqCode\":\"AC170558B944CBBE\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelate(
                "{\"reqCode\":\"AC170558B944CBBE\",\"data\":\"Task01_1\"}", "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals(List.of(13L), completed);
    }

    /**
     * 指定关联键（AGV 控制器走这条）：reqCode 命中关联键列就直接结算，不用满报文试值
     */
    @Test
    void shouldSettleByGivenCorrelateKey() {
        waiting.add(command(14L, "C0014", "1541954B96B1112", "{\"reqCode\":\"1541954B96B1112\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelateKey("1541954B96B1112",
                "{\"reqCode\":\"1541954B96B1112\",\"method\":\"end\"}", "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals("C0014", receipt.commandNo());
        assertEquals(List.of(14L), completed);
    }

    /**
     * 指定关联键没命中（比如关联键还没写进库、或动作配的是别的字段）时退回按报文内容认人，
     * 不能把厂商一条正常回调丢掉
     */
    @Test
    void shouldFallBackWhenGivenKeyMisses() {
        waiting.add(command(15L, "C0015", null,
                "{\"reqCode\":\"AC170558B944CBBE\",\"taskTyp\":\"Task01\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelateKey("AC170558B944CBBE",
                "{\"reqCode\":\"AC170558B944CBBE\",\"method\":\"end\"}", "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals(List.of(15L), completed, "关联键列是空的时候应靠报文内容兜底认出命令");
    }

    /**
     * 关联键给空白时直接走全文匹配（等价不传），不该报错
     */
    @Test
    void shouldFallBackWhenGivenKeyBlank() {
        waiting.add(command(16L, "C0016", "8FADAE4C08FD0046", "{\"reqCode\":\"8FADAE4C08FD0046\"}"));

        DeviceCallbackReceipt receipt = service.receiveByCorrelateKey("  ",
                "{\"reqCode\":\"8FADAE4C08FD0046\"}", "application/json");

        assertEquals(200, receipt.httpStatus());
        assertEquals(List.of(16L), completed);
    }

    /**
     * AGV 的动作定义
     */
    private static DeviceActionDO agvAction(String resultRule) {
        DeviceActionDO action = new DeviceActionDO();
        action.setDeviceTypeCode("AGV");
        action.setActionCode("genAgvSchedulingTask");
        action.setResultRule(resultRule);
        return action;
    }

    /**
     * 命令记录，带上回执要用的字段和报文内容
     */
    private static DeviceCommandDO command(Long id, String commandNo, String correlateValue, String payloadJson) {
        DeviceCommandDO command = new DeviceCommandDO();
        command.setId(id);
        command.setCommandNo(commandNo);
        command.setDeviceCode("AGV");
        command.setDeviceTypeCode("AGV");
        command.setActionCode("genAgvSchedulingTask");
        command.setTenantId(1L);
        command.setCorrelateValue(correlateValue);
        command.setPayloadJson(payloadJson);
        return command;
    }

    /**
     * 两个查询各自按列 / 按报文内容找，其余方法一律拒绝 —— 用到就说明测试跑偏了
     */
    private DeviceCommandMapper mapper() {
        return (DeviceCommandMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{DeviceCommandMapper.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    String needle = (String) args[0];
                    if ("selectWaitingByCorrelateValue".equals(name)) {
                        return waiting.stream()
                                .filter(row -> needle.equals(row.getCorrelateValue()))
                                .findFirst().map(DeviceCorrelateCallbackTest::copy).orElse(null);
                    }
                    if ("selectWaitingByPayloadContains".equals(name)) {
                        return waiting.stream()
                                .filter(row -> row.getPayloadJson() != null
                                        && row.getPayloadJson().contains(needle))
                                .findFirst().map(DeviceCorrelateCallbackTest::copy).orElse(null);
                    }
                    throw new UnsupportedOperationException(name);
                });
    }

    /**
     * 回快照，避免调用方改动影响「库」里的行
     */
    private static DeviceCommandDO copy(DeviceCommandDO row) {
        DeviceCommandDO copy = new DeviceCommandDO();
        copy.setId(row.getId());
        copy.setCommandNo(row.getCommandNo());
        copy.setDeviceCode(row.getDeviceCode());
        // 判定「回调是否到终态」要靠这两个字段反查动作，快照漏了就会静默当成「没配规则」
        copy.setDeviceTypeCode(row.getDeviceTypeCode());
        copy.setActionCode(row.getActionCode());
        copy.setTenantId(row.getTenantId());
        copy.setCorrelateValue(row.getCorrelateValue());
        copy.setPayloadJson(row.getPayloadJson());
        return copy;
    }

    /**
     * 只实现结算回调，记录被结算的命令编号
     */
    private DeviceCommandService commandService() {
        return (DeviceCommandService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{DeviceCommandService.class},
                (proxy, method, args) -> {
                    if ("completeCallback".equals(method.getName())) {
                        completed.add((Long) args[0]);
                        return completeAccepted;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 只实现按「设备类型 + 动作编码」查动作，返回当前测试设定的动作（可能为空）
     */
    private DeviceActionMapper actionMapper() {
        return (DeviceActionMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{DeviceActionMapper.class},
                (proxy, method, args) -> {
                    if ("selectByTypeAndAction".equals(method.getName())) {
                        return action;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

}

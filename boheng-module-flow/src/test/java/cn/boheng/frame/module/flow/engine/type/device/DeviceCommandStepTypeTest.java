package cn.boheng.frame.module.flow.engine.type.device;

import cn.boheng.frame.module.device.api.DeviceCommandApi;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.config.DeviceCommandConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * device.command 节点的产出结构与失败语义回归。
 *
 * <p>EventId / RequestId 这类协议字段不在这里处理：它们就是报文模板里的
 * ${uuid()}，节点只管把参数交给设备域。</p>
 */
class DeviceCommandStepTypeTest {

    /**
     * 固定运行编号，断言里直接比字面量更直观
     */
    private static final long EXECUTION_ID = 2001L;

    /**
     * 节点编码
     */
    private static final String NODE_ID = "rotate";

    /**
     * 造一个设备命令接口桩：返回一次同步成功
     *
     * @param parsedOutputJson 解析规则产出的结构化结果，null 表示没有解析规则
     */
    private DeviceCommandStepType stepReturning(String parsedOutputJson) {
        DeviceCommandApi api = mock(DeviceCommandApi.class);
        when(api.send(any())).thenAnswer(invocation -> {
            DeviceCommandResultDTO result = new DeviceCommandResultDTO();
            result.setSuccess(true);
            result.setStatus("SUCCEEDED");
            result.setCommandId(88L);
            result.setCommandNo("C88");
            result.setOutputJson("{\"ExecuteStatus\":0,\"ResultCode\":\"00000000\"}");
            result.setParsedOutputJson(parsedOutputJson);
            return result;
        });
        return new DeviceCommandStepType(api);
    }

    /**
     * 成功产出按规范字段给出，并把解析结果挂到 data 上；
     * 厂商的 ExecuteStatus / ResultCode 只出现在 raw 里。
     */
    @Test
    void shouldEmitNormalizedOutput() {
        DeviceCommandStepType step = stepReturning("{\"Volume\":12.5}");

        StepOutcome outcome = step.execute(context(NODE_ID, 0, 1));
        JSONObject output = JSONUtil.parseObj(outcome.getOutputJson());

        assertEquals(true, output.getBool("success"));
        assertNull(output.get("errorType"));
        assertNull(output.get("retryable"));
        assertEquals(88L, output.getLong("commandId"));
        assertEquals("C88", output.getStr("commandNo"));
        assertEquals("SUCCEEDED", output.getStr("status"));
        assertTrue(output.getStr("raw").contains("ExecuteStatus"));
        assertEquals(12.5, output.getJSONObject("data").getDouble("Volume"), 0.001);
        // 解析结果同时平铺，保证老流程按 ${node.Volume} 取值不失效
        assertEquals(12.5, output.getDouble("Volume"), 0.001);
    }

    /**
     * 设备域说这次失败不该重试时，节点必须把结论原样带出来，
     * 否则流程会按默认策略反复捶一台已经明确拒绝的设备。
     */
    @Test
    void shouldPassRetryableThroughOnFailure() {
        DeviceCommandApi api = mock(DeviceCommandApi.class);
        when(api.send(any())).thenReturn(failing(false));

        StepOutcome outcome = new DeviceCommandStepType(api).execute(context(NODE_ID, 0, 1));

        assertEquals(StepOutcome.Kind.FAILED, outcome.getKind());
        assertEquals(Boolean.FALSE, outcome.getRetryable());
        assertTrue(!outcome.isRetryable());
        assertTrue(outcome.getErrorMessage().contains("托盘未就位"));
    }

    /**
     * 设备没声明可重试性时保持历史行为：继续重试。
     */
    @Test
    void shouldKeepRetryableWhenDeviceSilent() {
        DeviceCommandApi api = mock(DeviceCommandApi.class);
        when(api.send(any())).thenReturn(failing(null));

        StepOutcome outcome = new DeviceCommandStepType(api).execute(context(NODE_ID, 0, 1));

        assertEquals(StepOutcome.Kind.FAILED, outcome.getKind());
        assertTrue(outcome.isRetryable());
        assertNull(outcome.getRetryable());
    }

    /**
     * 造一个失败的下发结果
     *
     * @param retryable 设备声明是否可重试，null 表示没声明
     */
    private DeviceCommandResultDTO failing(Boolean retryable) {
        DeviceCommandResultDTO result = new DeviceCommandResultDTO();
        result.setSuccess(false);
        result.setStatus("FAILED");
        result.setErrorType("DEVICE_REJECTED");
        result.setRetryable(retryable);
        result.setErrorMessage("设备拒绝执行：托盘未就位");
        return result;
    }

    /**
     * 指定节点、轮次、尝试次数的执行上下文
     */
    private StepExecutionContext context(String nodeId, int iterationNo, int attempt) {
        Map<String, Object> params = new LinkedHashMap<>();
        DeviceCommandConfig config = new DeviceCommandConfig();
        config.setDeviceCode("HPLC");
        config.setActionCode("trun");
        return new StepExecutionContext(EXECUTION_ID, nodeId, attempt, iterationNo, params, config, 10_000L);
    }

}

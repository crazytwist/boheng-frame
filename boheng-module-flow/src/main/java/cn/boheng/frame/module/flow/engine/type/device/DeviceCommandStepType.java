package cn.boheng.frame.module.flow.engine.type.device;

import cn.boheng.frame.module.device.api.DeviceCommandApi;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;
import cn.boheng.frame.module.device.api.dto.DeviceCommandSendReqDTO;
import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.DeviceCommandConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * device.command 调一次设备动作。只依赖设备契约 {@link DeviceCommandApi}，
 * 不反向依赖设备实现模块。回调动作下发并被设备受理后进入持久化等待，
 * 后台扫描命令终态后再由信号分发器结算流程节点。
 */
@Component
public class DeviceCommandStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 设备命令契约
     */
    private final DeviceCommandApi deviceCommandApi;

    public DeviceCommandStepType(DeviceCommandApi deviceCommandApi) {
        this.deviceCommandApi = deviceCommandApi;
    }

    @Override
    public String type() {
        return FlowConstants.STEP_DEVICE_COMMAND;
    }

    @Override
    public StepRole role() {
        return StepRole.ACTION;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.CALLBACK;
    }

    @Override
    public Class<?> configType() {
        return DeviceCommandConfig.class;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof DeviceCommandConfig device)) {
            issues.add(new FlowIssue(nodeId, "设备步骤缺少配置"));
            return;
        }
        if (StrUtil.isBlank(device.getDeviceCode())) {
            issues.add(new FlowIssue(nodeId, "设备步骤要选择设备"));
        }
        if (StrUtil.isBlank(device.getActionCode())) {
            issues.add(new FlowIssue(nodeId, "设备步骤要选择动作"));
        }
        // 设备在台账且动作启用需要查库，由 FlowGraphChecker 的 DeviceLookup 补齐
    }

    /**
     * 执行：下发命令。参数映射由 ParamResolver 解析进 params，
     * 这里按设备域要求序列化成 JSON 传过去
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        DeviceCommandConfig config = ctx.config();
        DeviceCommandSendReqDTO req = new DeviceCommandSendReqDTO();
        req.setDeviceCode(config.getDeviceCode());
        req.setActionCode(config.getActionCode());
        req.setParamsJson(ctx.getParams().isEmpty() ? null : JSONUtil.toJsonStr(ctx.getParams()));
        req.setTimeoutMs(ctx.getTimeoutMs());
        req.setSourceType(FlowConstants.SOURCE_TYPE_FLOW);
        req.setSourceRef("flow:" + ctx.getExecutionId() + ":" + ctx.getNodeId() + ":" + ctx.getAttempt());
        req.setAttempt(ctx.getAttempt());
        DeviceCommandResultDTO result = deviceCommandApi.send(req);
        if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
            String error = result == null ? "设备无响应" : result.getErrorMessage();
            return StepOutcome.failed(StrUtil.blankToDefault(error, "设备命令失败"));
        }
        if ("WAITING".equals(result.getStatus())) {
            return StepOutcome.waiting("DEVICE_COMMAND_CALLBACK", String.valueOf(result.getCommandId()));
        }
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        output.put("commandId", result.getCommandId());
        output.put("commandNo", result.getCommandNo());
        output.put("body", result.getOutputJson());
        return StepOutcome.success(JSONUtil.toJsonStr(output));
    }

}

package cn.boheng.frame.module.flow.engine.type.device;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.DeviceTaskConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.task.FlowDeviceTaskService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * device.task 设备异步任务。执行时把任务投递到队列（不直接调设备），
 * 机器端拉取执行后回报完成，再由信号路由器结算节点继续推进。
 * 与 device.command 的区别：command 是同步推（设备在线），task 是异步拉（设备可离线排队）。
 */
@Component
public class DeviceTaskStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 任务队列服务
     */
    @Resource
    private FlowDeviceTaskService deviceTaskService;

    @Override
    public String type() {
        return FlowConstants.STEP_DEVICE_TASK;
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
        return DeviceTaskConfig.class;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        if (!(config instanceof DeviceTaskConfig device)) {
            issues.add(new FlowIssue(nodeId, "设备任务缺少配置"));
            return;
        }
        if (StrUtil.isBlank(device.getActionCode())) {
            issues.add(new FlowIssue(nodeId, "设备任务要选择任务类型（动作）"));
        }
    }

    /**
     * 执行：入队并挂起等机器端回报。waitRef 用任务号，回报接口按它定位
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        DeviceTaskConfig config = ctx.config();
        String taskNo = deviceTaskService.enqueue(
                ctx.getExecutionId(), ctx.getNodeId(), ctx.getAttempt(), ctx.getIterationNo(),
                config.getDeviceCode(), config.getActionCode(), ctx.getParams());
        return StepOutcome.waiting("DEVICE_CALLBACK", taskNo);
    }

}

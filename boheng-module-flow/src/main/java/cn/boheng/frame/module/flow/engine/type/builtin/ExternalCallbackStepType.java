package cn.boheng.frame.module.flow.engine.type.builtin;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.engine.signal.FlowCallbackUrl;
import cn.boheng.frame.module.flow.engine.type.StepExecutionContext;
import cn.boheng.frame.module.flow.engine.type.StepExecutor;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.StepTypeDescriptor;
import cn.boheng.frame.module.flow.engine.type.config.ExternalCallbackConfig;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.WaitingReason;

/**
 * external.callback 等外部业务系统回报。
 *
 * <p>与 device.command 的区别是「回调打到哪」：设备只认命令号，回调进设备域，
 * 再由完成事件驱动节点；认流程的对方（LIMS / MES / 上游调度）能说清是哪一次执行哪一步，
 * 所以直接回调节点，少一跳，也不用让流程身份塞进设备协议。</p>
 *
 * <p>回调地址形如 {@code /app-api/flow/external-callback/{executionId}/{nodeId}?attempt=&token=}，
 * 令牌由 {@code FlowCallbackToken} 按执行/节点/尝试派生，服务端可重算校验，不必下发。</p>
 */
@Component
public class ExternalCallbackStepType implements StepTypeDescriptor, StepExecutor {

    /**
     * 拼回调地址。令牌由「执行 + 节点 + 尝试」派生，对方算不出来，
     * 必须由这里带出去，否则对方知道该打哪个路径也过不了校验
     */
    @Resource
    private FlowCallbackUrl callbackUrl;

    @Override
    public String type() {
        return FlowConstants.STEP_EXTERNAL_CALLBACK;
    }

    @Override
    public StepRole role() {
        return StepRole.WAIT;
    }

    @Override
    public CompletionMode completionMode() {
        return CompletionMode.SIGNAL;
    }

    @Override
    public Class<?> configType() {
        return ExternalCallbackConfig.class;
    }

    @Override
    public boolean retryable() {
        // 外部系统已经就这次尝试给了结论，重试等于逼对方重放一次业务
        return false;
    }

    @Override
    public void validate(Object config, String nodeId, List<FlowIssue> issues) {
        // 对方系统名只用于排查，允许为空；没有必须拦的配置
        if (config != null && config instanceof ExternalCallbackConfig external
                && external.getCounterparty() != null && external.getCounterparty().length() > 64) {
            issues.add(new FlowIssue(nodeId, "外部回调的对方系统名不能超过 64 字"));
        }
    }

    /**
     * 执行：挂起并交出去回调地址。
     * <p>waitRef 记「在等谁」，空则记 external，纯为排查用；产出里的 callbackUrl 带 token，
     * 落在 step_execution.output_data，运行详情可见，也能被下游节点引用转发给对方。</p>
     */
    @Override
    public StepOutcome execute(StepExecutionContext ctx) {
        ExternalCallbackConfig config = ctx.config();
        String counterparty = config == null ? null : StrUtil.trimToNull(config.getCounterparty());
        JSONObject output = new JSONObject();
        output.set("callbackUrl", callbackUrl.callbackUrl(ctx.getExecutionId(), ctx.getNodeId(), ctx.getAttempt()));
        output.set("attempt", ctx.getAttempt());
        output.set("counterparty", StrUtil.blankToDefault(counterparty, "external"));
        output.set("expiresInMs", ctx.getTimeoutMs());
        return StepOutcome.waitingWithOutput(WaitingReason.EXTERNAL_CONFIRMATION.name(),
                StrUtil.blankToDefault(counterparty, "external"), output.toString());
    }

}

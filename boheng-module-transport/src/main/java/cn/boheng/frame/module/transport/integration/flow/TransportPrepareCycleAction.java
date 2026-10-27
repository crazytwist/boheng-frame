package cn.boheng.frame.module.transport.integration.flow;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 准备一轮装载，确定当前轮次和可装载数量。 */
@Component
public class TransportPrepareCycleAction implements FlowBeanAction {
    private final TransportFlowActionSupport support;
    public TransportPrepareCycleAction(TransportFlowActionSupport support) { this.support = support; }
    @Override public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = support.input(env);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("cycleStatus", "PREPARED"); extra.put("cycleNo", input.getOrDefault("cycleNo", 1));
        extra.put("remainingItemCount", input.getOrDefault("remainingItemCount", 1));
        extra.put("continueLoading", input.getOrDefault("continueLoading", false));
        return support.output("TRANSPORT_PREPARE_CYCLE", input, extra);
    }
}

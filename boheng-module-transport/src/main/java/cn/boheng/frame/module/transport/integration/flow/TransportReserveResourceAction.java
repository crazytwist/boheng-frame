package cn.boheng.frame.module.transport.integration.flow;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 预约 AGV、仓储出料位等运输资源。 */
@Component
public class TransportReserveResourceAction implements FlowBeanAction {
    private final TransportFlowActionSupport support;
    public TransportReserveResourceAction(TransportFlowActionSupport support) { this.support = support; }
    @Override public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = support.input(env); Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("resourceStatus", "RESERVED"); extra.put("agvCode", input.getOrDefault("agvCode", "AGV-TEST-01"));
        extra.put("outputSlotCode", input.getOrDefault("outputSlotCode", "OUT-TEST-01")); extra.put("lockMode", "LOCAL");
        return support.output("TRANSPORT_RESERVE_RESOURCE", input, extra);
    }
}

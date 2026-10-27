package cn.boheng.frame.module.transport.integration.flow;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 根据剩余物料和 AGV 容量决定继续装载还是开始运输。 */
@Component
public class TransportDecideNextAction implements FlowBeanAction {
    private final TransportFlowActionSupport support;
    public TransportDecideNextAction(TransportFlowActionSupport support) { this.support = support; }
    @Override public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = support.input(env); Map<String, Object> extra = new LinkedHashMap<>();
        boolean more = Boolean.parseBoolean(String.valueOf(input.getOrDefault("continueLoading", false)));
        extra.put("continueLoading", more); extra.put("nextAction", more ? "LOAD_NEXT" : "START_TRANSPORT");
        return support.output("TRANSPORT_DECIDE_NEXT", input, extra);
    }
}

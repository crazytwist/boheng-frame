package cn.boheng.frame.module.transport.integration.flow;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 确认仓储、AGV 或目标端的双边交接。 */
@Component
public class TransportConfirmHandoverAction implements FlowBeanAction {
    private final TransportFlowActionSupport support;
    public TransportConfirmHandoverAction(TransportFlowActionSupport support) { this.support = support; }
    @Override public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = support.input(env); Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("handoverStatus", "CONFIRMED"); extra.put("providerConfirmed", true); extra.put("receiverConfirmed", true);
        return support.output("TRANSPORT_CONFIRM_HANDOVER", input, extra);
    }
}

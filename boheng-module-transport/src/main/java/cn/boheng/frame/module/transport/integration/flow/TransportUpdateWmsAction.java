package cn.boheng.frame.module.transport.integration.flow;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 交接确认后更新 WMS 物料位置。 */
@Component
public class TransportUpdateWmsAction implements FlowBeanAction {
    private final TransportFlowActionSupport support;
    public TransportUpdateWmsAction(TransportFlowActionSupport support) { this.support = support; }
    @Override public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = support.input(env); Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("wmsStatus", "UPDATED"); extra.put("movementId", input.getOrDefault("movementId", "WMS-MOVE-TEST"));
        extra.put("locationUpdated", true);
        return support.output("TRANSPORT_UPDATE_WMS", input, extra);
    }
}

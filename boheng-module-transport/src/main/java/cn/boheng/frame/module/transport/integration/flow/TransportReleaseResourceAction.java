package cn.boheng.frame.module.transport.integration.flow;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 释放本轮装载占用的资源。 */
@Component
public class TransportReleaseResourceAction implements FlowBeanAction {
    private final TransportFlowActionSupport support;
    public TransportReleaseResourceAction(TransportFlowActionSupport support) { this.support = support; }
    @Override public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = support.input(env); Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("resourceStatus", "RELEASED"); extra.put("released", true);
        return support.output("TRANSPORT_RELEASE_RESOURCE", input, extra);
    }
}

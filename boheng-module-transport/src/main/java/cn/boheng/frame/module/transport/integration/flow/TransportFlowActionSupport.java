package cn.boheng.frame.module.transport.integration.flow;

import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Transport Flow Bean 的公共上下文和结果工具。 */
@Component
public class TransportFlowActionSupport {
    /** 从 Flow 环境中读取启动参数。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> input(Map<String, Object> env) {
        Object input = env == null ? null : env.get("input");
        return input instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    /** 生成统一的 Transport 节点输出。 */
    public String output(String action, Map<String, Object> input, Map<String, Object> extra) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("transportOrderId", input.get("transportOrderId"));
        result.put("loadCycleId", input.get("loadCycleId"));
        result.put("status", "SUCCESS");
        if (extra != null) result.putAll(extra);
        return JSONUtil.toJsonStr(result);
    }
}

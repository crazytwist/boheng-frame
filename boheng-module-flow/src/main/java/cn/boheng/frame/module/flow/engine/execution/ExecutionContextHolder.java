package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionContextDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionContextMapper;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行上下文的装载与写入。表里每键一行，内存里拼成三层 Map：
 * input.*（启动入参）、node.&lt;nodeId&gt;.*（节点输出，同键新轮覆盖旧轮）、sys.*（系统变量）。
 * 表达式求值、参数映射、路由决策共用这一个视图。
 */
@Component
public class ExecutionContextHolder {

    /**
     * 上下文表
     */
    @Resource
    private FlowExecutionContextMapper contextMapper;

    /**
     * 装载一个运行的完整上下文
     */
    public Map<String, Object> loadEnv(Long executionId) {
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> sys = new LinkedHashMap<>();
        // node.{nodeId}.{key} -> value，同键按 id 顺序后者覆盖前者（新轮覆盖旧轮）
        Map<String, Object> node = new LinkedHashMap<>();
        List<FlowExecutionContextDO> rows = contextMapper.selectByExecutionId(executionId);
        for (FlowExecutionContextDO row : rows) {
            Object value = parseValue(row.getValueData());
            switch (row.getNamespace() == null ? "" : row.getNamespace()) {
                case FlowConstants.NS_INPUT -> input.put(row.getContextKey(), value);
                case FlowConstants.NS_SYSTEM -> sys.put(row.getContextKey(), value);
                case FlowConstants.NS_NODE -> node.put(row.getNodeId() + "." + row.getContextKey(), value);
                default -> {
                }
            }
        }
        // 展平 node：求值时既能写 node.a.b，也能整体取 node
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("input", input);
        env.put("sys", sys);
        env.put("node", flatten(node));
        return env;
    }

    /**
     * 写启动入参。启动时一次性写入
     */
    public void saveInput(Long executionId, Map<String, Object> params) {
        if (params == null) {
            return;
        }
        params.forEach((key, value) -> save(executionId, FlowConstants.NS_INPUT, null, 0, key, value));
    }

    /**
     * 写系统变量
     */
    public void saveSystem(Long executionId, String key, Object value) {
        save(executionId, FlowConstants.NS_SYSTEM, null, 0, key, value);
    }

    /**
     * 写节点输出。outputMapping 为空时整个输出对象按 output 键存
     */
    public void saveNodeOutput(Long executionId, String nodeId, int iterationNo, String outputJson, String outputMapping) {
        JSONObject output = outputJson == null || outputJson.isBlank()
                ? new JSONObject() : JSONUtil.parseObj(outputJson);
        Map<String, Object> mapping = outputMapping == null || outputMapping.isBlank()
                ? null : JSONUtil.parseObj(outputMapping);
        if (mapping == null || mapping.isEmpty()) {
            save(executionId, FlowConstants.NS_NODE, nodeId, iterationNo, "output", output);
            return;
        }
        for (String field : mapping.keySet()) {
            Object value = output.get(field);
            Object contextKeyObj = mapping.get(field);
            String contextKey = contextKeyObj == null ? null : contextKeyObj.toString();
            save(executionId, FlowConstants.NS_NODE, nodeId, iterationNo,
                    contextKey == null || contextKey.isBlank() ? field : contextKey, value);
        }
    }

    /**
     * 按参数映射解析一个节点的输入。来源：INPUT 启动入参 / NODE 前置输出 / LITERAL 固定值
     */
    public Map<String, Object> resolveParams(RuntimeNode node, Map<String, Object> env) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<FlowMapping> mappings = node.getInputMapping();
        if (mappings == null) {
            return params;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) env.getOrDefault("input", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> flatNode = (Map<String, Object>) env.getOrDefault("node", Map.of());
        for (FlowMapping mapping : mappings) {
            if (mapping == null || mapping.getParam() == null) {
                continue;
            }
            switch (mapping.getSource() == null ? "" : mapping.getSource()) {
                case FlowConstants.SOURCE_INPUT -> params.put(mapping.getParam(), input.get(mapping.getField()));
                case FlowConstants.SOURCE_NODE -> params.put(mapping.getParam(),
                        flatNode.get(mapping.getNodeId() + "." + mapping.getField()));
                case FlowConstants.SOURCE_LITERAL -> params.put(mapping.getParam(), plainValue(mapping.getValue()));
                default -> {
                }
            }
        }
        return params;
    }

    /**
     * 落一行上下文
     */
    private void save(Long executionId, String namespace, String nodeId, int iterationNo, String key, Object value) {
        FlowExecutionContextDO row = FlowExecutionContextDO.builder()
                .executionId(executionId)
                .namespace(namespace)
                .nodeId(nodeId)
                .iterationNo(iterationNo)
                .contextKey(key)
                .valueData(JSONUtil.toJsonStr(value))
                .build();
        contextMapper.insert(row);
    }

    /**
     * JSON 值转 Java 对象
     */
    private Object parseValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSONUtil.parse(json);
    }

    /**
     * 固定值尽量还原成基础类型（数字/布尔/字符串），别让所有字面量都变成字符串
     */
    private Object plainValue(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            // 不是整数，继续试小数
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    /**
     * 把 node.a.b 形态的键拼成嵌套 Map，同时保留平铺键——
     * 求值器两种写法都要能命中
     */
    private Map<String, Object> flatten(Map<String, Object> dotted) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : dotted.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            if (parts.length == 1) {
                result.put(entry.getKey(), entry.getValue());
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> bucket = (Map<String, Object>) result.computeIfAbsent(parts[0], k -> new LinkedHashMap<String, Object>());
            for (int i = 1; i < parts.length - 1; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> next = (Map<String, Object>) bucket.computeIfAbsent(parts[i], k -> new LinkedHashMap<String, Object>());
                bucket = next;
            }
            bucket.put(parts[parts.length - 1], entry.getValue());
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

}

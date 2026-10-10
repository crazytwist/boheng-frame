package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionContextDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionContextMapper;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.temporal.Temporal;
import java.util.Date;
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

    private static final Logger log = LoggerFactory.getLogger(ExecutionContextHolder.class);

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
                case FlowConstants.SOURCE_INPUT -> params.put(mapping.getParam(),
                        pathGet(input, mapping.getField()));
                case FlowConstants.SOURCE_NODE -> params.put(mapping.getParam(),
                        pathGet(flatNode.get(mapping.getNodeId()), mapping.getField()));
                case FlowConstants.SOURCE_LITERAL -> params.put(mapping.getParam(), plainValue(mapping.getValue()));
                default -> {
                }
            }
        }
        return params;
    }

    /**
     * 按路径从嵌套结构里取值。支持点分下钻与数组下标：
     * {@code children}、{@code children[0].executionId}、{@code a.b[2].c}。
     * 空路径返回根对象本身；任何一段取不到返回 null（映射到参数为 null，不抛异常）。
     *
     * <p>这是参数映射引用结构化字段的唯一实现，INPUT 与 NODE 来源共用：
     * 子流程扇出后，下游就能写 {@code detail[0].executionId} 取第一个子运行的编号。
     */
    static Object pathGet(Object root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return root;
        }
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            int bracket = segment.indexOf('[');
            if (bracket < 0) {
                current = readKey(current, segment);
                continue;
            }
            if (bracket > 0) {
                current = readKey(current, segment.substring(0, bracket));
            }
            while (current != null) {
                int close = segment.indexOf(']', bracket);
                if (close < 0) {
                    break;
                }
                current = readIndex(current, segment.substring(bracket + 1, close).trim());
                bracket = segment.indexOf('[', close);
                if (bracket < 0) {
                    break;
                }
            }
        }
        return current;
    }

    /**
     * 按键取值。空键原样返回；Map 按键，其它类型取不到
     */
    private static Object readKey(Object current, String key) {
        if (key.isEmpty()) {
            return current;
        }
        if (current instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    /**
     * 按下标取值。List 或数组按索引，越界或非法下标返回 null
     */
    private static Object readIndex(Object current, String idx) {
        int index;
        try {
            index = Integer.parseInt(idx);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (current instanceof List<?> list) {
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        if (current.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(current);
            return index >= 0 && index < length ? java.lang.reflect.Array.get(current, index) : null;
        }
        return null;
    }

    /**
     * 落一行上下文
     */
    private void save(Long executionId, String namespace, String nodeId, int iterationNo, String key, Object value) {
        // hutool 的 JSONUtil.toJsonStr 对 String 直接返回原值（视为已是 JSON），
        // 落到 MySQL JSON 列会被当成无效 JSON 报错；非 String 走 toJsonStr 正常
        String valueData = encodeValue(value);
        FlowExecutionContextDO row = FlowExecutionContextDO.builder()
                .executionId(executionId)
                .namespace(namespace)
                .nodeId(nodeId)
                .iterationNo(iterationNo)
                .contextKey(key)
                .valueData(valueData)
                .build();
        try {
            contextMapper.insert(row);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // row_key 唯一索引兜底：并发 settle 抢跑同节点同轮同一 key，
            // 先到者写入，后续 settle 跳过，保持先到的语义
            log.debug("[flow][engine] 上下文行已存在，跳过: ns={} node={} iter={} key={}",
                    namespace, nodeId, iterationNo, key);
        }
    }

    /**
     * 把任意值序列化成合法的 JSON 值字符串（带引号的字符串 / true / 数字 / {} / []）。
     * 写入 MySQL JSON 列时整列视为 JSON 文档，
     * 字符串必须带引号，hutool JSONUtil.toJsonStr 在 String 上直接返回原值会报错
     */
    private String encodeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return JSONUtil.quote(value.toString());
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Date
                || value instanceof Temporal || value.getClass().isEnum()) {
            return JSONUtil.toJsonStr(value);
        }
        // Map / List / Bean 等复合类型走 hutool 默认序列化
        return JSONUtil.toJsonStr(value);
    }

    /**
     * JSON 值转 Java 对象。值在 MySQL JSON 列里以合法 JSON 文档存储，
     * 读取时按首字符分派：对象 / 数组 / 字符串 / 原生字面量
     */
    private Object parseValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        char first = json.charAt(0);
        switch (first) {
            case '{' -> {
                return JSONUtil.parseObj(json);
            }
            case '[' -> {
                return JSONUtil.parseArray(json);
            }
            case '"' -> {
                // 带引号 JSON 字符串——自行解引号（hutool 没有 unquote API）
                return unquoteJsonString(json);
            }
            default -> {
                // 数字 / false / true / null 等字面量
                if ("null".equals(json)) {
                    return null;
                }
                if ("true".equals(json) || "false".equals(json)) {
                    return Boolean.parseBoolean(json);
                }
                try {
                    return Long.parseLong(json);
                } catch (NumberFormatException ignored) {
                }
                try {
                    return Double.parseDouble(json);
                } catch (NumberFormatException ignored) {
                }
                return json;
            }
        }
    }

    /**
     * 解 JSON 字符串字面量。处理 \" \\ \n \r \t \/ \ uXXXX 等转义；
     * hutool 5.8 内部对 String 字面量直接丢给 parseObj 解析，本工具自给自足
     */
    private String unquoteJsonString(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"') {
            throw new IllegalArgumentException("不是合法的 JSON 字符串字面量: " + text);
        }
        StringBuilder sb = new StringBuilder(text.length() - 2);
        int end = text.length() - 1;
        for (int i = 1; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < end) {
                char next = text.charAt(++i);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < end) {
                            sb.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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

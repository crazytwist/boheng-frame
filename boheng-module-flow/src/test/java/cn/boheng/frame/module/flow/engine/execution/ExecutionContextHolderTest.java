package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.engine.execution.RuntimeNode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行上下文测试：参数映射（INPUT/NODE/LITERAL 三来源 + 结构化路径取值）与
 * JSON 编解码回归。不启动 Spring，resolveParams 是纯函数，编解码方法走反射。
 *
 * <p>JSON 编解码是上一轮踩过坑的核心：String 必须 quote、JSON 字符串字面量要自解引号。
 */
class ExecutionContextHolderTest {

    private final ExecutionContextHolder holder = new ExecutionContextHolder();

    /**
     * 三种来源一次覆盖：入参、前置输出、固定值
     */
    @Test
    void resolvesInputNodeAndLiteral() {
        RuntimeNode node = node(List.of(
                mapping("taskNo", FlowConstants.SOURCE_INPUT, null, "taskNo", null),
                mapping("firstSample", FlowConstants.SOURCE_NODE, "register", "samples[0].id", null),
                mapping("retry", FlowConstants.SOURCE_LITERAL, null, null, "3")));
        Map<String, Object> env = env(
                Map.of("taskNo", "WQ-001"),
                Map.of("register", Map.of("samples", List.of(Map.of("id", "S1", "val", 7.2)))));
        Map<String, Object> params = holder.resolveParams(node, env);
        assertEquals("WQ-001", params.get("taskNo"));
        assertEquals("S1", params.get("firstSample"));
        assertEquals(3L, params.get("retry"));
    }

    /**
     * 无映射时返回空 map，不 NPE
     */
    @Test
    void noMappingReturnsEmpty() {
        RuntimeNode node = node(List.of());
        assertTrue(holder.resolveParams(node, Map.of()).isEmpty());
    }

    /**
     * 能力目录默认参数作为基底：节点未配映射时直接生效
     */
    @Test
    void defaultParamsActsAsBaseWhenNoMapping() {
        RuntimeNode node = node(List.of());
        node.setDefaultParams("{\"Seconds\":\"1000\",\"Mode\":\"A\"}");
        Map<String, Object> params = holder.resolveParams(node, env(Map.of(), Map.of()));
        assertEquals("1000", params.get("Seconds"));
        assertEquals("A", params.get("Mode"));
    }

    /**
     * 节点映射同名覆盖默认参数，不同名保留
     */
    @Test
    void nodeMappingOverridesDefaultParams() {
        RuntimeNode node = node(List.of(
                mapping("Seconds", FlowConstants.SOURCE_LITERAL, null, null, "1000")));
        node.setDefaultParams("{\"Seconds\":\"500\",\"Mode\":\"A\"}");
        Map<String, Object> params = holder.resolveParams(node, env(Map.of(), Map.of()));
        assertEquals(1000L, params.get("Seconds"));
        assertEquals("A", params.get("Mode"));
    }

    /**
     * 参数集提供最低优先级默认值，步骤默认参数及节点映射可以依次覆盖。
     */
    @Test
    void paramSetIsTheLowestPriorityParameterBase() {
        RuntimeNode node = node(List.of(
                mapping("sampleId", FlowConstants.SOURCE_LITERAL, null, null, "S002")));
        node.setDefaultParams("{\"temperature\":20,\"mode\":\"AUTO\"}");
        Map<String, Object> params = holder.resolveParams(node, env(Map.of(), Map.of()),
                Map.of("sampleId", "S001", "temperature", 25, "operator", "robot"));
        assertEquals("S002", params.get("sampleId"));
        assertEquals(20, params.get("temperature"));
        assertEquals("AUTO", params.get("mode"));
        assertEquals("robot", params.get("operator"));
    }

    /**
     * 默认参数 JSON 非法时忽略，不抛异常，映射照常
     */
    @Test
    void invalidDefaultParamsIgnored() {
        RuntimeNode node = node(List.of(
                mapping("retry", FlowConstants.SOURCE_LITERAL, null, null, "3")));
        node.setDefaultParams("not-json");
        Map<String, Object> params = holder.resolveParams(node, env(Map.of(), Map.of()));
        assertEquals(3L, params.get("retry"));
        assertEquals(1, params.size());
    }

    /**
     * 路径取值：点分下钻 + 数组下标 + 越界/缺失安全返回 null
     */
    @Test
    void pathGetWalksNestedStructures() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", Map.of("b", Map.of("c", 42)));
        root.put("list", List.of(Map.of("name", "x"), Map.of("name", "y")));

        assertNull(ExecutionContextHolder.pathGet(null, "a"));
        assertEquals(root, ExecutionContextHolder.pathGet(root, ""));
        assertEquals(root, ExecutionContextHolder.pathGet(root, null));
        assertEquals(42, ExecutionContextHolder.pathGet(root, "a.b.c"));
        assertEquals("x", ExecutionContextHolder.pathGet(root, "list[0].name"));
        assertEquals("y", ExecutionContextHolder.pathGet(root, "list[1].name"));
        assertNull(ExecutionContextHolder.pathGet(root, "list[2].name"));
        assertNull(ExecutionContextHolder.pathGet(root, "a.b.missing"));
        assertNull(ExecutionContextHolder.pathGet(root, "a.b.c.d"));
    }

    /**
     * 固定值还原基础类型：整数、小数、布尔、字符串
     */
    @Test
    void literalRestoresPrimitiveTypes() {
        RuntimeNode node = node(List.of(
                mapping("i", FlowConstants.SOURCE_LITERAL, null, null, "42"),
                mapping("d", FlowConstants.SOURCE_LITERAL, null, null, "7.35"),
                mapping("b", FlowConstants.SOURCE_LITERAL, null, null, "true"),
                mapping("s", FlowConstants.SOURCE_LITERAL, null, null, "hello")));
        Map<String, Object> params = holder.resolveParams(node, env(Map.of(), Map.of()));
        assertEquals(42L, params.get("i"));
        assertEquals(7.35, params.get("d"));
        assertEquals(true, params.get("b"));
        assertEquals("hello", params.get("s"));
    }

    /**
     * encodeValue：String 必须带引号（否则 MySQL JSON 列报 Invalid JSON text）
     */
    @Test
    void encodeValueQuotesStrings() throws Exception {
        assertEquals("\"WQ-001\"", invoke("encodeValue", "WQ-001"));
        assertEquals("7.35", invoke("encodeValue", 7.35));
        assertEquals("true", invoke("encodeValue", true));
        assertEquals("null", invoke("encodeValue", (Object) null));
        assertEquals("{\"a\":1}", invoke("encodeValue", Map.of("a", 1)));
    }

    /**
     * parseValue：JSON 字符串字面量自解引号，含转义与 Unicode 转义
     */
    @Test
    void parseValueUnquotesJsonStringLiteral() throws Exception {
        assertEquals("WQ-001", invoke("parseValue", "\"WQ-001\""));
        assertEquals("a\nb", invoke("parseValue", "\"a\\nb\""));
        // Unicode 转义 4e2d -> 中：源码里不能出现字面反斜杠+u 序列（Java 会提前做 Unicode 转义），
        // 用 0x5c 生成反斜杠字符，再拼上普通文本 u4e2d 组成待解析的 JSON 字面量
        String backslash = String.valueOf((char) 0x5c);
        assertEquals("中", invoke("parseValue", "\"" + backslash + "u4e2d" + "\""));
        assertEquals(7L, invoke("parseValue", "7"));
        assertEquals(7.35, invoke("parseValue", "7.35"));
        assertEquals(true, invoke("parseValue", "true"));
        assertNull(invoke("parseValue", "null"));
        assertInstanceOf(JSONObject.class, invoke("parseValue", "{\"a\":1}"));
        assertInstanceOf(JSONArray.class, invoke("parseValue", "[1,2]"));
    }

    /**
     * unquoteJsonString 各种转义
     */
    @Test
    void unquoteHandlesEscapes() throws Exception {
        assertEquals("a\"b", invoke("unquoteJsonString", "\"a\\\"b\""));
        assertEquals("a\\b", invoke("unquoteJsonString", "\"a\\\\b\""));
        assertEquals("a\tb", invoke("unquoteJsonString", "\"a\\tb\""));
        assertEquals("a/b", invoke("unquoteJsonString", "\"a\\/b\""));
    }

    // ==================== 工具 ====================

    private static FlowMapping mapping(String param, String source, String nodeId, String field, String value) {
        FlowMapping mapping = new FlowMapping();
        mapping.setParam(param);
        mapping.setSource(source);
        mapping.setNodeId(nodeId);
        mapping.setField(field);
        mapping.setValue(value);
        return mapping;
    }

    private static RuntimeNode node(List<FlowMapping> mappings) {
        RuntimeNode node = new RuntimeNode();
        node.setNodeId("n");
        node.setInputMapping(mappings);
        return node;
    }

    private static Map<String, Object> env(Map<String, Object> input, Map<String, Object> node) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("input", input);
        env.put("sys", Map.of());
        env.put("node", node);
        return env;
    }

    /**
     * 反射调用私有方法
     */
    private Object invoke(String method, Object... args) throws Exception {
        Method target = null;
        for (Method methodCandidate : ExecutionContextHolder.class.getDeclaredMethods()) {
            if (methodCandidate.getName().equals(method)) {
                target = methodCandidate;
                break;
            }
        }
        if (target == null) {
            throw new NoSuchMethodException(method);
        }
        target.setAccessible(true);
        return target.invoke(holder, args);
    }

}

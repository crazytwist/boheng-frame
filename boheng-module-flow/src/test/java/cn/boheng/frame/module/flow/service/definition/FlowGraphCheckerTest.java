package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.flow.engine.expression.ExpressionEvaluator;
import cn.boheng.frame.module.flow.engine.route.FlowRouteAction;
import cn.boheng.frame.module.flow.engine.route.FlowRouteRegistry;
import cn.boheng.frame.module.flow.engine.type.StepTypeRegistry;
import cn.boheng.frame.module.flow.engine.type.builtin.LoopStepType;
import cn.boheng.frame.module.flow.engine.type.builtin.RouteStepType;
import cn.boheng.frame.module.flow.engine.type.device.DeviceCommandStepType;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图校验器 v3：结构（无环 / 前置存在 / join 一致）、类型配置、
 * 路由出口、循环区间、发布指纹。不启动 Spring，注册表用反射手工装配。
 */
class FlowGraphCheckerTest {

    /**
     * 设备在台账、动作启用的回调
     */
    private final FlowGraphChecker.DeviceLookup lookup = new FlowGraphChecker.DeviceLookup() {

        @Override
        public DeviceSummaryDTO getByCode(String deviceCode) {
            if ("GONE".equals(deviceCode)) {
                return null;
            }
            DeviceSummaryDTO device = new DeviceSummaryDTO();
            device.setDeviceCode(deviceCode);
            device.setDeviceName("测试设备");
            device.setDeviceTypeCode("CENTRIFUGE");
            return device;
        }

        @Override
        public boolean hasEnabledAction(String deviceTypeCode, String actionCode) {
            return !"STOP".equals(actionCode);
        }
    };

    /**
     * 子流程引用：elisa-assay 已发布，其余没有
     */
    private final FlowGraphChecker.SubflowLookup subflowRef =
            (pipelineKey, version) -> "elisa-assay".equals(pipelineKey);

    /**
     * 两步直线设备流程 + 参数映射取上一步输出，发布检查应为空
     */
    @Test
    void publishAcceptsLinearDeviceSteps() {
        FlowNodeDraft read = deviceStep("read", "读板", "DEV-2", "READ_PLATE", mapping("od", "spin", "od"));
        read.setDependsOn(List.of("spin"));
        List<FlowIssue> issues = FlowGraphChecker.checkPublish(
                List.of(deviceStep("spin", "离心", "DEV-1", "SPIN"), read),
                lookup, registry(), subflowRef);
        assertTrue(issues.isEmpty(), issues.toString());
    }

    /**
     * 参数映射只能取直接前置；不是前置要报错
     */
    @Test
    void rejectsMappingFromNonPredecessor() {
        FlowNodeDraft third = deviceStep("archive", "归档", "DEV-1", "SPIN", mapping("od", "spin", "od"));
        third.setDependsOn(List.of("read"));
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(List.of(
                deviceStep("spin", "离心", "DEV-1", "SPIN"),
                deviceStep("read", "读板", "DEV-2", "READ_PLATE"),
                third));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("不是前置")));
    }

    /**
     * 依赖成环必须拦下
     */
    @Test
    void rejectsCycle() {
        FlowNodeDraft a = deviceStep("spin", "离心", "DEV-1", "SPIN");
        FlowNodeDraft b = deviceStep("read", "读板", "DEV-2", "READ_PLATE");
        a.setDependsOn(List.of("read"));
        b.setDependsOn(List.of("spin"));
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(List.of(a, b));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("环")));
    }

    /**
     * 单前置不能用 ANY_SUCCESS
     */
    @Test
    void rejectsAnySuccessWithSingleDependency() {
        FlowNodeDraft second = deviceStep("read", "读板", "DEV-2", "READ_PLATE");
        second.setDependsOn(List.of("spin"));
        second.setJoinPolicy("ANY_SUCCESS");
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(List.of(
                deviceStep("spin", "离心", "DEV-1", "SPIN"), second));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("ANY_SUCCESS")));
    }

    /**
     * 设备不存在、动作停用，发布期都要说清楚
     */
    @Test
    void publishNamesMissingDeviceAndDisabledAction() {
        List<FlowIssue> empty = FlowGraphChecker.checkPublish(List.of(), lookup, registry(), subflowRef);
        assertEquals("至少添加一个节点", empty.get(0).getMessage());

        List<FlowIssue> missing = FlowGraphChecker.checkPublish(
                List.of(deviceStep("spin", "离心", "GONE", "SPIN")), lookup, registry(), subflowRef);
        assertTrue(missing.stream().anyMatch(issue -> issue.getMessage().contains("GONE")));

        List<FlowIssue> disabled = FlowGraphChecker.checkPublish(
                List.of(deviceStep("spin", "离心", "DEV-1", "STOP")), lookup, registry(), subflowRef);
        assertTrue(disabled.stream().anyMatch(issue -> issue.getMessage().contains("没有启用的动作")));
    }

    /**
     * 路由节点：default 出口必须有；出口指向不存在的节点要报错
     */
    @Test
    void validatesRouteBranches() {
        FlowNodeDraft route = new FlowNodeDraft();
        route.setNodeId("route_od");
        route.setName("按 OD 路由");
        route.setStepType(FlowConstants.STEP_ROUTE);
        route.setTypeConfig("{\"router\":{\"kind\":\"expression\",\"expression\":\"od > 1.0 ? 'high' : 'default'\"},"
                + "\"branches\":{\"high\":[\"alarm\"],\"default\":[\"read\"]}}");
        route.setDependsOn(List.of("read"));
        route.setBranches(Map.of("high", List.of("alarm"), "default", List.of("read")));
        // 没有 alarm 节点：出口目标不存在
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(List.of(
                deviceStep("read", "读板", "DEV-2", "READ_PLATE"), route));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("不存在")));

        // 缺 default 出口：类型校验拦下
        FlowNodeDraft noDefault = new FlowNodeDraft();
        noDefault.setNodeId("route_bad");
        noDefault.setName("没有兜底");
        noDefault.setStepType(FlowConstants.STEP_ROUTE);
        noDefault.setTypeConfig("{\"router\":{\"kind\":\"expression\",\"expression\":\"'high'\"},"
                + "\"branches\":{\"high\":[\"read\"]}}");
        noDefault.setDependsOn(List.of());
        List<FlowIssue> publishIssues = FlowGraphChecker.checkPublish(
                List.of(deviceStep("read", "读板", "DEV-2", "READ_PLATE"), noDefault),
                lookup, registry(), subflowRef);
        assertTrue(publishIssues.stream().anyMatch(issue -> issue.getMessage().contains("default")));
    }

    /**
     * 循环节点：区间端点不存在要报错
     */
    @Test
    void validatesLoopRange() {
        FlowNodeDraft loop = new FlowNodeDraft();
        loop.setNodeId("loop_read");
        loop.setName("循环读板");
        loop.setStepType(FlowConstants.STEP_LOOP);
        loop.setTypeConfig("{\"bodyEntryNodeId\":\"read\",\"bodyExitNodeId\":\"ghost\","
                + "\"maxIterations\":5,\"whileExpression\":\"sys.needMore == true\"}");
        loop.setLoopBodyEntry("read");
        loop.setLoopBodyExit("ghost");
        loop.setDependsOn(List.of());
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(List.of(
                deviceStep("read", "读板", "DEV-2", "READ_PLATE"), loop));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("循环体入口或出口不存在")));
    }

    /**
     * 控制类节点不能挂能力目录
     */
    @Test
    void rejectsControlNodeWithStepId() {
        FlowNodeDraft route = new FlowNodeDraft();
        route.setNodeId("route_x");
        route.setName("路由");
        route.setStepType(FlowConstants.STEP_ROUTE);
        route.setStepId(123L);
        route.setDependsOn(List.of());
        List<FlowIssue> issues = FlowGraphChecker.checkPublish(List.of(route), lookup, registry(), subflowRef);
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("控制类节点")));
    }

    /**
     * 同一张图指纹稳定；改类型配置后指纹必须变
     */
    @Test
    void checksumChangesWhenConfigChanges() {
        List<FlowNodeDraft> nodes = List.of(deviceStep("spin", "离心", "DEV-1", "SPIN"));
        String first = FlowGraphChecker.checksum(nodes);
        assertEquals(first, FlowGraphChecker.checksum(nodes));
        nodes.get(0).setTypeConfig("{\"deviceCode\":\"DEV-1\",\"actionCode\":\"STOP\"}");
        assertFalse(first.equals(FlowGraphChecker.checksum(nodes)));
    }

    // ==================== 测试工具 ====================

    /**
     * 造一个设备动作节点。dependsOn 由调用方补
     */
    private static FlowNodeDraft deviceStep(String nodeId, String name, String deviceCode,
                                            String actionCode, FlowMapping... mappings) {
        FlowNodeDraft draft = new FlowNodeDraft();
        draft.setNodeId(nodeId);
        draft.setName(name);
        draft.setStepType(FlowConstants.STEP_DEVICE_COMMAND);
        draft.setTypeConfig("{\"deviceCode\":\"" + deviceCode + "\",\"actionCode\":\"" + actionCode + "\"}");
        draft.setDependsOn(List.of());
        draft.setTimeoutMs(FlowConstants.DEFAULT_TIMEOUT_MS);
        draft.setMaxAttempts(1);
        draft.setFailStrategy(FlowConstants.FAIL_FAST);
        if (mappings != null && mappings.length > 0) {
            draft.setMappings(new java.util.ArrayList<>(List.of(mappings)));
        }
        return draft;
    }

    /**
     * 从某节点取一个字段的映射
     */
    private static FlowMapping mapping(String param, String nodeId, String field) {
        FlowMapping mapping = new FlowMapping();
        mapping.setParam(param);
        mapping.setSource(FlowConstants.SOURCE_NODE);
        mapping.setNodeId(nodeId);
        mapping.setField(field);
        return mapping;
    }

    /**
     * 手工装配类型注册表：设备 / 路由 / 循环 三个内置类型。
     * RouteStepType 需要的决策器白名单为空表——本组测试不走 Bean 决策。
     */
    private static StepTypeRegistry registry() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        FlowRouteRegistry routeRegistry = new FlowRouteRegistry();
        // FlowRouteRegistry 的 actionsProvider 是 ObjectProvider（SPI 允许零实现），
        // 测试里注入空提供器，再 init() 建空索引
        inject(routeRegistry, "actionsProvider", emptyRouteActions());
        routeRegistry.init();
        StepTypeRegistry registry = new StepTypeRegistry();
        inject(registry, "descriptors", List.of(
                new DeviceCommandStepType(null),
                new RouteStepType(evaluator, routeRegistry),
                new LoopStepType(evaluator)));
        inject(registry, "executors", List.of(
                new DeviceCommandStepType(null),
                new RouteStepType(evaluator, routeRegistry)));
        registry.init();
        return registry;
    }

    /**
     * 空的路由决策器提供器。init() 只消费 orderedStream()，其余方法不参与本组测试
     */
    private static ObjectProvider<FlowRouteAction> emptyRouteActions() {
        return new ObjectProvider<>() {
            @Override
            public FlowRouteAction getObject() {
                return null;
            }

            @Override
            public FlowRouteAction getObject(Object... args) {
                return null;
            }

            @Override
            public FlowRouteAction getIfAvailable() {
                return null;
            }

            @Override
            public FlowRouteAction getIfAvailable(Supplier<FlowRouteAction> defaultSupplier) {
                return defaultSupplier.get();
            }

            @Override
            public void ifAvailable(Consumer<FlowRouteAction> dependencyConsumer) {
            }

            @Override
            public FlowRouteAction getIfUnique() {
                return null;
            }

            @Override
            public FlowRouteAction getIfUnique(Supplier<FlowRouteAction> defaultSupplier) {
                return defaultSupplier.get();
            }

            @Override
            public void ifUnique(Consumer<FlowRouteAction> dependencyConsumer) {
            }

            @Override
            public Stream<FlowRouteAction> stream() {
                return Stream.empty();
            }

            @Override
            public Stream<FlowRouteAction> orderedStream() {
                return Stream.empty();
            }
        };
    }

    /**
     * 反射注入 @Resource 字段。仅测试用
     */
    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("测试装配失败: " + fieldName, ex);
        }
    }

}

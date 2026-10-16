package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.flow.engine.type.StepTypeRegistry;
import cn.boheng.frame.module.flow.engine.type.config.DeviceCommandConfig;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.enums.JoinPolicy;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 流程图校验器。保存草稿做结构校验，发布做全量校验（fail-fast）：
 * v2 的防线是「依赖服务端按顺序写」，v3 放开 depends_on 后，
 * 「画得出跑不了」的防线全部由这里的校验矩阵补回。
 * 不访问数据库；设备与子流程引用由调用方通过回调回答。
 */
public final class FlowGraphChecker {

    /**
     * 节点编码规则
     */
    private static final Pattern NODE_ID = Pattern.compile(FlowConstants.NODE_ID_REGEX);

    /**
     * 分支出口名规则
     */
    private static final Pattern BRANCH_KEY = Pattern.compile(FlowConstants.BRANCH_KEY_REGEX);

    private FlowGraphChecker() {
    }

    /**
     * 保存草稿时的结构校验。设备可以先空着，人还在选。
     */
    public static List<FlowIssue> checkStructure(List<FlowNodeDraft> nodes) {
        List<FlowIssue> issues = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return issues;
        }
        if (nodes.size() > FlowConstants.MAX_NODES) {
            issues.add(new FlowIssue(null, "一条流程最多 " + FlowConstants.MAX_NODES + " 个节点"));
        }
        Set<String> seen = new HashSet<>();
        for (FlowNodeDraft node : nodes) {
            checkIdentity(issues, seen, node);
        }
        issues.addAll(checkGraph(nodes));
        return issues;
    }

    /**
     * 发布时的全量校验：结构 + 类型配置 + 图关系 + 设备台账。
     *
     * @param lookup     设备查询回调
     * @param registry   类型注册表（type_config 的 Descriptor 校验）
     * @param subflowRef 子流程引用回调：目标是否已发布
     */
    public static List<FlowIssue> checkPublish(List<FlowNodeDraft> nodes, DeviceLookup lookup,
                                                StepTypeRegistry registry, SubflowLookup subflowRef) {
        List<FlowIssue> issues = new ArrayList<>(checkStructure(nodes));
        if (nodes == null || nodes.isEmpty()) {
            issues.add(new FlowIssue(null, "至少添加一个节点"));
            return issues;
        }
        for (FlowNodeDraft node : nodes) {
            String label = labelOf(node);
            // 类型已注册
            if (StrUtil.isBlank(node.getStepType()) || !registry.isRegistered(node.getStepType())) {
                issues.add(new FlowIssue(node.getNodeId(),
                        "节点【" + label + "】的步骤类型【" + StrUtil.nullToEmpty(node.getStepType()) + "】未注册"));
                continue;
            }
            // 控制类节点不允许挂能力目录
            if (StepTypeRegistry.isControlType(node.getStepType()) && node.getStepId() != null) {
                issues.add(new FlowIssue(node.getNodeId(),
                        "控制类节点【" + label + "】不能关联步骤表里的能力"));
            }
            // Descriptor 的类型专属校验
            Object config = registry.parseConfig(node.getStepType(), node.getTypeConfig());
            registry.getDescriptor(node.getStepType()).validate(config, node.getNodeId(), issues);
            // 设备类节点：设备在台账且动作启用
            if (FlowConstants.STEP_DEVICE_COMMAND.equals(node.getStepType())) {
                checkDevice(issues, node, config, lookup);
            }
            // 子流程引用：目标必须已发布
            if (FlowConstants.STEP_SUBFLOW.equals(node.getStepType()) && subflowRef != null
                    && StrUtil.isNotBlank(node.getSubflowTargetKey())
                    && !subflowRef.isPublished(node.getSubflowTargetKey(), null)) {
                issues.add(new FlowIssue(node.getNodeId(),
                        "子流程【" + node.getSubflowTargetKey() + "】不是已发布状态"));
            }
        }
        checkOutputContract(issues, nodes);
        return issues;
    }

    /**
     * 输出映射契约校验（发布期）：
     * 1. 能力声明了输出（flow_step.output_schema 非空）时，output_mapping 的源字段必须落在声明里——
     *    声明即契约，写错字段名运行期只会静默取到 null；
     * 2. 两个源字段归一化到同一个上下文键 → 后者覆盖前者，是配置事故。
     * 未声明输出的能力（含控制类节点、输出动态的能力）只做第 2 条，不做字段白名单。
     */
    private static void checkOutputContract(List<FlowIssue> issues, List<FlowNodeDraft> nodes) {
        for (FlowNodeDraft node : nodes) {
            if (node == null || StrUtil.isBlank(node.getOutputMapping())) {
                continue;
            }
            JSONObject mapping;
            try {
                mapping = JSONUtil.parseObj(node.getOutputMapping());
            } catch (RuntimeException ex) {
                issues.add(new FlowIssue(node.getNodeId(),
                        "节点【" + labelOf(node) + "】的输出映射不是合法 JSON"));
                continue;
            }
            if (mapping.isEmpty()) {
                continue;
            }
            Set<String> declared = declaredOutputFields(node.getOutputSchema());
            Map<String, String> contextKeyOwner = new LinkedHashMap<>();
            for (String field : mapping.keySet()) {
                if (StrUtil.isBlank(field) || field.startsWith("__")) {
                    continue;
                }
                // 1. 源字段必须是能力声明的输出
                if (!declared.isEmpty() && !declared.contains(field)) {
                    issues.add(new FlowIssue(node.getNodeId(),
                            "节点【" + labelOf(node) + "】的输出映射字段【" + field + "】不在能力声明的输出里（声明："
                                    + String.join("、", declared) + "）"));
                }
                // 2. 上下文键不能撞车
                String contextKey = StrUtil.blankToDefault(mapping.getStr(field), field);
                String previous = contextKeyOwner.put(contextKey, field);
                if (previous != null) {
                    issues.add(new FlowIssue(node.getNodeId(),
                            "节点【" + labelOf(node) + "】的输出映射里【" + previous + "】与【" + field
                                    + "】都归一化到上下文键【" + contextKey + "】，后者会覆盖前者"));
                }
            }
        }
    }

    /**
     * 读输出声明的字段名集合。空 / 坏数据返回空集（= 不做白名单校验）
     */
    private static Set<String> declaredOutputFields(String outputSchema) {
        if (StrUtil.isBlank(outputSchema)) {
            return Set.of();
        }
        try {
            JSONObject declared = JSONUtil.parseObj(outputSchema);
            Set<String> fields = new LinkedHashSet<>();
            for (String key : declared.keySet()) {
                if (StrUtil.isNotBlank(key) && !key.startsWith("__")) {
                    fields.add(key);
                }
            }
            return fields;
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    /**
     * 发布指纹。同一张图每次发布得到同一个值。
     */
    public static String checksum(List<FlowNodeDraft> nodes) {
        StringBuilder text = new StringBuilder();
        if (nodes != null) {
            List<FlowNodeDraft> sorted = new ArrayList<>(nodes);
            sorted.sort(Comparator.comparing(FlowNodeDraft::getNodeId, Comparator.nullsFirst(String::compareTo)));
            for (FlowNodeDraft node : sorted) {
                text.append(node.getNodeId()).append('|')
                        .append(node.getStepType()).append('|')
                        .append(StrUtil.nullToEmpty(node.getTypeConfig())).append('|')
                        .append(node.getDependsOn() == null ? "" : node.getDependsOn().toString()).append('|')
                        .append(StrUtil.nullToEmpty(node.getJoinPolicy())).append('|')
                        .append(node.getTimeoutMs()).append('|')
                        .append(node.getMaxAttempts()).append('|')
                        .append(StrUtil.nullToEmpty(node.getFailStrategy())).append('|')
                        .append(StrUtil.nullToEmpty(node.getOutputMapping())).append('|');
                if (node.getBranches() != null) {
                    List<String> keys = new ArrayList<>(node.getBranches().keySet());
                    keys.sort(String::compareTo);
                    for (String key : keys) {
                        text.append(key).append('=').append(node.getBranches().get(key)).append(',');
                    }
                }
                appendMappings(text, node);
                text.append('\n');
            }
        }
        return DigestUtil.sha256Hex(text.toString());
    }

    /**
     * 发布失败时把多条原因收成一句
     */
    public static String join(List<FlowIssue> issues) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) {
                text.append('；');
            }
            text.append(issues.get(i).getMessage());
        }
        return text.toString();
    }

    // ==================== 图关系校验 ====================

    /**
     * 图级校验：前置存在、无环、join 与前置数一致、参数映射必须是直接前驱、
     * loop 区间合法、route 出口目标存在。
     */
    private static List<FlowIssue> checkGraph(List<FlowNodeDraft> nodes) {
        List<FlowIssue> issues = new ArrayList<>();
        Map<String, FlowNodeDraft> byId = new LinkedHashMap<>();
        for (FlowNodeDraft node : nodes) {
            if (StrUtil.isNotBlank(node.getNodeId())) {
                byId.put(node.getNodeId(), node);
            }
        }
        for (FlowNodeDraft node : nodes) {
            String label = labelOf(node);
            // 前置存在
            for (String dep : node.getDependsOn() == null ? List.<String>of() : node.getDependsOn()) {
                if (!byId.containsKey(dep)) {
                    issues.add(new FlowIssue(node.getNodeId(),
                            "节点【" + label + "】的前置【" + dep + "】不存在"));
                }
            }
            // join 与前置数一致：单前置用 ANY_SUCCESS 没有意义且易错
            if (node.getDependsOn() != null && node.getDependsOn().size() <= 1
                    && JoinPolicy.ANY_SUCCESS.name().equals(node.getJoinPolicy())) {
                issues.add(new FlowIssue(node.getNodeId(),
                        "节点【" + label + "】只有一个前置，不能用 ANY_SUCCESS"));
            }
            // 参数映射：NODE 来源必须取直接前驱
            for (FlowMapping mapping : node.getMappings() == null ? List.<FlowMapping>of() : node.getMappings()) {
                if (mapping == null || !FlowConstants.SOURCE_NODE.equals(mapping.getSource())) {
                    continue;
                }
                if (node.getDependsOn() == null || !node.getDependsOn().contains(mapping.getNodeId())) {
                    issues.add(new FlowIssue(node.getNodeId(),
                            "节点【" + label + "】的参数【" + StrUtil.nullToEmpty(mapping.getParam())
                                    + "】只能取直接前置的输出，【" + StrUtil.nullToEmpty(mapping.getNodeId()) + "】不是前置"));
                }
            }
            // route：出口目标必须存在
            if (FlowConstants.STEP_ROUTE.equals(node.getStepType()) && node.getBranches() != null) {
                for (Map.Entry<String, List<String>> entry : node.getBranches().entrySet()) {
                    for (String target : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                        if (!byId.containsKey(target)) {
                            issues.add(new FlowIssue(node.getNodeId(),
                                    "路由节点【" + label + "】的出口【" + entry.getKey()
                                            + "】指向的节点【" + target + "】不存在"));
                        }
                    }
                }
            }
            // loop：区间端点存在且不自指
            if (FlowConstants.STEP_LOOP.equals(node.getStepType())) {
                boolean entryMissing = StrUtil.isBlank(node.getLoopBodyEntry())
                        || !byId.containsKey(node.getLoopBodyEntry());
                boolean exitMissing = StrUtil.isBlank(node.getLoopBodyExit())
                        || !byId.containsKey(node.getLoopBodyExit());
                if (entryMissing || exitMissing) {
                    issues.add(new FlowIssue(node.getNodeId(),
                            "循环节点【" + label + "】的循环体入口或出口不存在"));
                } else if (node.getLoopBodyEntry().equals(node.getNodeId())
                        || node.getLoopBodyExit().equals(node.getNodeId())) {
                    issues.add(new FlowIssue(node.getNodeId(),
                            "循环节点【" + label + "】的区间不能包含自己"));
                }
            }
        }
        detectCycle(nodes, issues);
        return issues;
    }

    /**
     * 环检测：Kahn 拓扑排序，入度清不完即有环
     */
    private static void detectCycle(List<FlowNodeDraft> nodes, List<FlowIssue> issues) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (FlowNodeDraft node : nodes) {
            String id = node.getNodeId();
            indegree.putIfAbsent(id, 0);
            for (String dep : node.getDependsOn() == null ? List.<String>of() : node.getDependsOn()) {
                adjacency.computeIfAbsent(dep, k -> new ArrayList<>()).add(id);
                indegree.merge(id, 1, Integer::sum);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                queue.add(id);
            }
        });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : adjacency.getOrDefault(current, List.of())) {
                int degree = indegree.merge(next, -1, Integer::sum);
                if (degree == 0) {
                    queue.add(next);
                }
            }
        }
        if (visited < indegree.size()) {
            issues.add(new FlowIssue(null, "依赖关系有环，请检查节点的连线"));
        }
    }

    // ==================== 节点身份 ====================

    /**
     * 编码、名称、类型
     */
    private static void checkIdentity(List<FlowIssue> issues, Set<String> seen, FlowNodeDraft node) {
        if (node == null) {
            return;
        }
        String label = labelOf(node);
        if (StrUtil.isBlank(node.getNodeId()) || !NODE_ID.matcher(node.getNodeId()).matches()) {
            issues.add(new FlowIssue(node.getNodeId(),
                    "节点【" + label + "】的编码须是小写字母开头，只含小写、数字和下划线"));
        } else if (!seen.add(node.getNodeId())) {
            issues.add(new FlowIssue(node.getNodeId(), "节点编码【" + node.getNodeId() + "】重复"));
        }
        if (StrUtil.isBlank(node.getName()) || node.getName().length() > 128) {
            issues.add(new FlowIssue(node.getNodeId(), "节点【" + label + "】的名称不能为空，且不超过 128 字"));
        }
        if (StrUtil.isBlank(node.getStepType())) {
            issues.add(new FlowIssue(node.getNodeId(), "节点【" + label + "】要选择步骤类型"));
        }
    }

    /**
     * 设备类节点的台账校验
     */
    private static void checkDevice(List<FlowIssue> issues, FlowNodeDraft node, Object config, DeviceLookup lookup) {
        if (!(config instanceof DeviceCommandConfig device) || StrUtil.isBlank(device.getDeviceCode())) {
            issues.add(new FlowIssue(node.getNodeId(), "设备步骤【" + labelOf(node) + "】还没选择设备"));
            return;
        }
        if (StrUtil.isBlank(device.getActionCode())) {
            issues.add(new FlowIssue(node.getNodeId(), "设备步骤【" + labelOf(node) + "】还没选择动作"));
            return;
        }
        if (lookup == null) {
            return;
        }
        DeviceSummaryDTO summary = lookup.getByCode(device.getDeviceCode());
        if (summary == null) {
            issues.add(new FlowIssue(node.getNodeId(), "设备【" + device.getDeviceCode() + "】不存在"));
            return;
        }
        if (!lookup.hasEnabledAction(summary.getDeviceTypeCode(), device.getActionCode())) {
            issues.add(new FlowIssue(node.getNodeId(),
                    "设备【" + summary.getDeviceName() + "】没有启用的动作【" + device.getActionCode() + "】"));
        }
    }

    /**
     * 指纹里的参数映射段
     */
    private static void appendMappings(StringBuilder text, FlowNodeDraft node) {
        if (node.getMappings() == null) {
            return;
        }
        List<FlowMapping> mappings = new ArrayList<>(node.getMappings());
        mappings.sort(Comparator.comparing(FlowMapping::getParam, Comparator.nullsFirst(String::compareTo)));
        for (FlowMapping mapping : mappings) {
            text.append(mapping.getParam()).append('=')
                    .append(mapping.getSource()).append(':')
                    .append(StrUtil.nullToEmpty(mapping.getNodeId())).append(':')
                    .append(StrUtil.nullToEmpty(mapping.getField())).append(':')
                    .append(StrUtil.nullToEmpty(mapping.getValue())).append(',');
        }
    }

    /**
     * 提示里优先用名称
     */
    private static String labelOf(FlowNodeDraft node) {
        if (node != null && StrUtil.isNotBlank(node.getName())) {
            return node.getName();
        }
        return node == null ? "" : StrUtil.nullToEmpty(node.getNodeId());
    }

    /**
     * 发布校验问设备域的两个问题。流程模块不自己查设备表。
     */
    public interface DeviceLookup {

        /**
         * 按编码取设备。没有则返回 null。
         */
        DeviceSummaryDTO getByCode(String deviceCode);

        /**
         * 该类型下动作是否仍启用。
         */
        boolean hasEnabledAction(String deviceTypeCode, String actionCode);

    }

    /**
     * 发布校验问子流程引用的问题：目标是否已发布。
     */
    public interface SubflowLookup {

        /**
         * 目标编码 + 版本是否处于已发布状态。版本空 = 任意已发布版本
         */
        boolean isPublished(String pipelineKey, Integer version);

    }

}

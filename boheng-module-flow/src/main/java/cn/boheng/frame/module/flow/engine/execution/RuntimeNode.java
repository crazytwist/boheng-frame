package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行期冻结的节点视图。把 FlowNodeDO 的 JSON 字段解析成结构，
 * 图上的每个节点一份，调度器只和这个视图打交道，不再碰 DO 的字符串字段。
 */
@Data
public class RuntimeNode {

    /**
     * 节点编码
     */
    private String nodeId;

    /**
     * 展示顺序
     */
    private Integer sortNo;

    /**
     * 节点引用的能力（flow_step.id）。控制类节点为空
     */
    private Long stepId;

    /**
     * 生效的步骤类型：节点级覆盖优先，否则取步骤上的
     */
    private String stepType;

    /**
     * 生效的类型配置 JSON：节点级优先
     */
    private String typeConfig;

    /**
     * 参数映射（已解析）
     */
    private List<cn.boheng.frame.module.flow.service.definition.model.FlowMapping> inputMapping = new ArrayList<>();

    /**
     * 输出映射 JSON：{输出字段: 上下文键}
     */
    private String outputMapping;

    /**
     * 运行覆盖 JSON：timeoutMs / maxAttempts / failureStrategy
     */
    private String runConfig;

    /**
     * 汇合策略
     */
    private String joinPolicy;

    /**
     * 前置失败策略
     */
    private String joinFailurePolicy;

    /**
     * 出口映射（route 用）：出口名 -> 目标节点编码列表
     */
    private Map<String, List<String>> branches;

    /**
     * 前置节点编码
     */
    private List<String> dependsOn = new ArrayList<>();

    /**
     * 从 DO 构建。JSON 字段解析失败按空处理——发布校验已拦住坏数据
     */
    public static RuntimeNode of(FlowNodeDO row) {
        RuntimeNode node = new RuntimeNode();
        node.setNodeId(row.getNodeId());
        node.setSortNo(row.getSortNo());
        node.setStepId(row.getStepId());
        node.setStepType(row.getStepType());
        node.setTypeConfig(row.getTypeConfig());
        node.setJoinPolicy(row.getJoinPolicy());
        node.setJoinFailurePolicy(row.getJoinFailurePolicy());
        node.setOutputMapping(row.getOutputMapping());
        node.setRunConfig(row.getRunConfig());
        if (row.getDependsOn() != null && !row.getDependsOn().isBlank()) {
            node.setDependsOn(JSONUtil.toList(row.getDependsOn(), String.class));
        }
        if (row.getInputMapping() != null && !row.getInputMapping().isBlank()) {
            node.setInputMapping(JSONUtil.toList(row.getInputMapping(),
                    cn.boheng.frame.module.flow.service.definition.model.FlowMapping.class));
        }
        if (row.getBranches() != null && !row.getBranches().isBlank()) {
            Map<String, List<String>> branches = new LinkedHashMap<>();
            JSONUtil.parseObj(row.getBranches()).forEach((key, value)
                    -> branches.put(key, JSONUtil.toList(JSONUtil.parseArray(value == null ? "[]" : value.toString()), String.class)));
            node.setBranches(branches);
        }
        return node;
    }

    /**
     * 运行覆盖里的超时。没有覆盖返回 null
     */
    public Long runTimeoutMs() {
        return runValue("timeoutMs", Long.class);
    }

    /**
     * 运行覆盖里的尝试上限。没有覆盖返回 null
     */
    public Integer runMaxAttempts() {
        return runValue("maxAttempts", Integer.class);
    }

    /**
     * 运行覆盖里的失败策略。没有覆盖返回 null
     */
    public String runFailureStrategy() {
        return runValue("failureStrategy", String.class);
    }

    /**
     * 从 runConfig JSON 里取一个值
     */
    private <T> T runValue(String key, Class<T> type) {
        if (runConfig == null || runConfig.isBlank()) {
            return null;
        }
        try {
            Object value = JSONUtil.parseObj(runConfig).get(key);
            if (value == null) {
                return null;
            }
            return JSONUtil.toBean(value.toString(), type);
        } catch (RuntimeException ex) {
            return null;
        }
    }

}

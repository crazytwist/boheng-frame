package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行期冻结的流程图。启动时从定义版本构建一次，
 * 之后运行期间不再查定义表——保证同一次运行看到的是同一张图。
 */
@Data
public class PipelineGraph {

    /**
     * 流程编码
     */
    private String pipelineKey;

    /**
     * 版本
     */
    private Integer version;

    /**
     * 定义编号
     */
    private Long definitionId;

    /**
     * 发布指纹
     */
    private String checksum;

    /**
     * 节点清单，按 nodeId 索引
     */
    private Map<String, RuntimeNode> nodes = new LinkedHashMap<>();

    /**
     * 各节点的能力目录快照（stepId -> FlowStepDO）。控制类节点没有
     */
    private Map<Long, FlowStepDO> steps = new HashMap<>();

    /**
     * 哪些节点直接依赖某个节点（反查扇出）
     */
    private Map<String, List<String>> dependents = new HashMap<>();

    /**
     * 从库行构建：节点行 + 关联的能力行
     */
    public static PipelineGraph of(String pipelineKey, Integer version, Long definitionId, String checksum,
                                    List<FlowNodeDO> nodeRows, Map<Long, FlowStepDO> stepRows) {
        PipelineGraph graph = new PipelineGraph();
        graph.setPipelineKey(pipelineKey);
        graph.setVersion(version);
        graph.setDefinitionId(definitionId);
        graph.setChecksum(checksum);
        graph.setSteps(stepRows);
        for (FlowNodeDO row : nodeRows) {
            RuntimeNode node = RuntimeNode.of(row);
            // 类型解析：节点级覆盖优先；引用能力时取步骤上的
            if (node.getStepType() == null && row.getStepId() != null) {
                FlowStepDO step = stepRows.get(row.getStepId());
                if (step != null) {
                    node.setStepType(step.getStepType());
                    if (node.getTypeConfig() == null) {
                        node.setTypeConfig(step.getTypeConfig());
                    }
                }
            }
            graph.getNodes().put(node.getNodeId(), node);
        }
        for (RuntimeNode node : graph.getNodes().values()) {
            for (String dep : node.getDependsOn()) {
                graph.getDependents().computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getNodeId());
            }
        }
        return graph;
    }

    /**
     * 取节点。不存在返回 null
     */
    public RuntimeNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 节点能力。控制类节点或能力缺失返回 null
     */
    public FlowStepDO stepOf(RuntimeNode node) {
        return node.getStepId() == null ? null : steps.get(node.getStepId());
    }

    /**
     * 图里除给定节点外的全部节点编码
     */
    public List<String> allNodeIdsExcept(String excludeNodeId) {
        List<String> ids = new ArrayList<>();
        for (String id : nodes.keySet()) {
            if (!id.equals(excludeNodeId)) {
                ids.add(id);
            }
        }
        return ids;
    }

}

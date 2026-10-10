package cn.boheng.frame.module.flow.engine.execution.scheduler;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import cn.boheng.frame.module.flow.engine.execution.PipelineGraph;
import cn.boheng.frame.module.flow.engine.execution.RuntimeNode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.collection.CollUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 分支激活与裁剪。拿路由节点已经算好的出口 key：
 * ① 查 branches 找目标节点编码；② 未选中出口的目标及其全部下游标 SKIPPED/BRANCH；
 * ③ 下游的下游（被裁剪节点挡住的）标 SKIPPED/UPSTREAM。
 * 决策求值不在这里——RouteStepType 只算 key，这里只执行图操作。
 */
@Component
public class BranchResolver {

    /**
     * 节点执行表
     */
    @Resource
    private FlowStepExecutionMapper stepExecutionMapper;

    /**
     * 应用一次路由决策：被选中的出口目标保持 PENDING（正常等就绪判定），
     * 其余出口的目标连同下游统一裁剪
     *
     * @param graph          冻结图
     * @param routeNode      路由节点
     * @param selectedBranch 选中的出口名
     * @param iterationNo    当前迭代
     * @param routeRow       路由节点执行行（用来判断目标是否可裁剪）
     */
    public void apply(PipelineGraph graph, RuntimeNode routeNode, String selectedBranch,
                      int iterationNo, FlowStepExecutionDO routeRow) {
        List<String> activated = routeNode.getBranches() == null
                ? List.of() : routeNode.getBranches().getOrDefault(selectedBranch, List.of());
        // 收集全部出口目标
        Set<String> allTargets = new HashSet<>();
        routeNode.getBranches().values().forEach(allTargets::addAll);
        // 未选中的目标：裁剪
        Set<String> skipped = new HashSet<>(allTargets);
        skipped.removeAll(activated);
        // 从被裁剪的目标出发，把只可能由它们到达的下游全部标 UPSTREAM
        Deque<String> queue = new ArrayDeque<>(skipped);
        Set<String> visited = new HashSet<>(skipped);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> dependents = graph.getDependents().get(current);
            if (CollUtil.isEmpty(dependents)) {
                continue;
            }
            for (String next : dependents) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        // 落库：直接目标是出口未选中（BRANCH），传导下去的是连带（UPSTREAM）
        for (String nodeId : visited) {
            FlowStepExecutionDO row = stepExecutionMapper.selectLatestAttempt(
                    routeRow.getExecutionId(), nodeId, iterationNo);
            if (row == null || !"PENDING".equals(row.getStatus())) {
                continue;
            }
            row.setStatus("SKIPPED");
            row.setSkipReason(skipped.contains(nodeId)
                    ? SkippedReasonOf(routeNode, nodeId) : "UPSTREAM");
            row.setFinishedAt(LocalDateTime.now());
            stepExecutionMapper.updateById(row);
        }
    }

    /**
     * 直接目标按出口归属判断原因：不是本次选中出口的目标记 BRANCH
     */
    private String SkippedReasonOf(RuntimeNode routeNode, String nodeId) {
        return "BRANCH";
    }

}

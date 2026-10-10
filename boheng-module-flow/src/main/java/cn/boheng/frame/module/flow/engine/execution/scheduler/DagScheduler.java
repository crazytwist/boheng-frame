package cn.boheng.frame.module.flow.engine.execution.scheduler;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.engine.execution.PipelineGraph;
import cn.boheng.frame.module.flow.engine.execution.RuntimeNode;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 就绪判定器。纯函数：输入冻结图和节点执行行，输出就绪节点清单。
 *
 * <p>判定规则（一期支持直线 / 并行 / 合并 / 分支裁剪，循环由引擎按迭代展开后走同一套判定）：
 * <ul>
 *   <li>节点当前迭代的最新尝试行是 PENDING，且全部前置按 join_policy 满足 → 就绪</li>
 *   <li>前置 SUCCESS、SKIPPED/BRANCH（出口未选中被裁剪）视为满足</li>
 *   <li>前置 FAILED / DEAD / SKIPPED/FAILURE、UPSTREAM 按 join_failure_policy：
 *       FAIL 时本节点跟着失败，SKIP 时本节点被裁剪</li>
 *   <li>ANY_SUCCESS：任一前置满足即就绪；全部前置终态且无一满足时按失败策略处理</li>
 *   <li>ALL_DONE：全部前置进入终态即就绪，成败都放行</li>
 * </ul>
 */
public final class DagScheduler {

    private DagScheduler() {
    }

    /**
     * 一轮就绪判定结果
     */
    public record Decision(List<String> readyNodeIds, Map<String, String> skipByFailure) {
    }

    /**
     * 判定一轮。
     *
     * @param graph    冻结图
     * @param latestByNode 节点当前迭代的最新尝试行（nodeId -&gt; 行）
     */
    public static Decision decide(PipelineGraph graph, Map<String, FlowStepExecutionDO> latestByNode) {
        List<String> ready = new ArrayList<>();
        Map<String, String> skipByFailure = new HashMap<>();
        for (RuntimeNode node : graph.getNodes().values()) {
            FlowStepExecutionDO row = latestByNode.get(node.getNodeId());
            if (row == null) {
                // 循环体尚未展开的节点：等待所属 loop 驱动，不参与本轮判定
                continue;
            }
            if (!"PENDING".equals(row.getStatus())) {
                continue;
            }
            List<FlowStepExecutionDO> dependencyRows = new ArrayList<>();
            boolean missing = false;
            for (String dep : node.getDependsOn()) {
                FlowStepExecutionDO depRow = latestByNode.get(dep);
                if (depRow == null) {
                    missing = true;
                    break;
                }
                dependencyRows.add(depRow);
            }
            if (missing) {
                continue;
            }
            String joinPolicy = StrUtil.blankToDefault(node.getJoinPolicy(),
                    FlowConstants.DEFAULT_JOIN_POLICY);
            String failurePolicy = StrUtil.blankToDefault(node.getJoinFailurePolicy(),
                    FlowConstants.DEFAULT_JOIN_FAILURE_POLICY);
            Outcome outcome = evaluate(joinPolicy, failurePolicy, dependencyRows);
            switch (outcome) {
                case READY -> ready.add(node.getNodeId());
                case FAIL -> skipByFailure.put(node.getNodeId(), "FAILURE");
                case WAIT -> {
                }
                default -> {
                }
            }
        }
        return new Decision(ready, skipByFailure);
    }

    /**
     * 判定结论
     */
    private enum Outcome {

        /** 就绪 */
        READY,

        /** 还要等（前置未全部终态） */
        WAIT,

        /** 前置判失败，本节点按策略处理 */
        FAIL

    }

    /**
     * 按汇合策略评估前置
     */
    private static Outcome evaluate(String joinPolicy, String failurePolicy, List<FlowStepExecutionDO> dependencies) {
        boolean allTerminal = dependencies.stream().allMatch(DagScheduler::isTerminal);
        if (!allTerminal) {
            // ANY_SUCCESS 的提前放行：已有一个成功
            if ("ANY_SUCCESS".equals(joinPolicy) && dependencies.stream().anyMatch(DagScheduler::isSatisfied)) {
                return Outcome.READY;
            }
            return Outcome.WAIT;
        }
        switch (joinPolicy) {
            case "ALL_DONE":
                return Outcome.READY;
            case "ANY_SUCCESS":
                return dependencies.stream().anyMatch(DagScheduler::isSatisfied)
                        ? Outcome.READY : Outcome.FAIL;
            case "ALL_SUCCESS":
            default:
                return dependencies.stream().allMatch(DagScheduler::isSatisfied)
                        ? Outcome.READY : Outcome.FAIL;
        }
    }

    /**
     * 终态判定：不再变化的状态
     */
    public static boolean isTerminal(FlowStepExecutionDO row) {
        String status = row.getStatus();
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "DEAD".equals(status)
                || "SKIPPED".equals(status) || "CANCELLED".equals(status);
    }

    /**
     * 满足判定：成功，或「出口未选中」的裁剪（裁剪不是失败）
     */
    public static boolean isSatisfied(FlowStepExecutionDO row) {
        if ("SUCCESS".equals(row.getStatus())) {
            return true;
        }
        return "SKIPPED".equals(row.getStatus())
                && ("BRANCH".equals(row.getSkipReason()) || "UPSTREAM".equals(row.getSkipReason()));
    }

    /**
     * 前置判失败时本节点怎么处理：SKIP 裁剪，FAIL 跟着失败
     */
    public static boolean shouldSkip(String failurePolicy) {
        return "SKIP".equals(failurePolicy);
    }

}

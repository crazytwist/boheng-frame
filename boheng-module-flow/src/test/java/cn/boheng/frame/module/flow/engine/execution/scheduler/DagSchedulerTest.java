package cn.boheng.frame.module.flow.engine.execution.scheduler;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.engine.execution.PipelineGraph;
import cn.boheng.frame.module.flow.engine.execution.RuntimeNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 就绪判定器纯函数测试。不启动 Spring，直接构造冻结图与节点执行行。
 *
 * <p>重点是 skip_reason 的三值语义：
 * BRANCH（出口未选中）/ UPSTREAM（上游连带）在汇合时视为「满足」，
 * FAILURE（前置失败）视为「不满足」——这是分支裁剪后下游能否继续跑的关键。
 */
class DagSchedulerTest {

    /**
     * 两个前置都成功，默认 ALL_SUCCESS → 就绪
     */
    @Test
    void allSuccessIsReady() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "b"), null, null),
                Map.of("a", row("SUCCESS", null), "b", row("SUCCESS", null), "join", row("PENDING", null)));
        assertEquals(List.of("join"), decision.readyNodeIds());
        assertTrue(decision.skipByFailure().isEmpty());
    }

    /**
     * 前置被分支裁剪（出口未选中），汇合视为满足 → 就绪
     */
    @Test
    void branchSkippedIsSatisfied() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "b"), null, null),
                Map.of("a", row("SKIPPED", "BRANCH"), "b", row("SUCCESS", null), "join", row("PENDING", null)));
        assertEquals(List.of("join"), decision.readyNodeIds());
    }

    /**
     * 前置因上游连带被裁剪，同样视为满足 → 就绪
     */
    @Test
    void upstreamSkippedIsSatisfied() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a"), null, null),
                Map.of("a", row("SKIPPED", "UPSTREAM"), "join", row("PENDING", null)));
        assertEquals(List.of("join"), decision.readyNodeIds());
    }

    /**
     * 前置因自身前置失败被裁剪，不视为满足 → 判失败
     */
    @Test
    void failureSkippedIsNotSatisfied() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a"), null, null),
                Map.of("a", row("SKIPPED", "FAILURE"), "join", row("PENDING", null)));
        assertTrue(decision.readyNodeIds().isEmpty());
        assertEquals(Map.of("join", "FAILURE"), decision.skipByFailure());
    }

    /**
     * 前置 FAILED，默认 ALL_SUCCESS → 判失败
     */
    @Test
    void failedDependencyFailsNode() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "b"), null, null),
                Map.of("a", row("FAILED", null), "b", row("SUCCESS", null), "join", row("PENDING", null)));
        assertTrue(decision.readyNodeIds().isEmpty());
        assertTrue(decision.skipByFailure().containsKey("join"));
    }

    /**
     * ANY_SUCCESS：已有一个成功即提前放行，不等其余前置
     */
    @Test
    void anySuccessReleasesEarly() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "b"), "ANY_SUCCESS", null),
                Map.of("a", row("SUCCESS", null), "b", row("PENDING", null), "join", row("PENDING", null)));
        assertEquals(List.of("join"), decision.readyNodeIds());
    }

    /**
     * ANY_SUCCESS：全部前置终态但无一成功 → 判失败
     */
    @Test
    void anySuccessAllFailedFails() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "b"), "ANY_SUCCESS", null),
                Map.of("a", row("FAILED", null), "b", row("SKIPPED", "FAILURE"), "join", row("PENDING", null)));
        assertTrue(decision.readyNodeIds().isEmpty());
        assertTrue(decision.skipByFailure().containsKey("join"));
    }

    /**
     * ALL_DONE：前置全部终态即就绪，成败都放行
     */
    @Test
    void allDoneReadyEvenWithFailure() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "b"), "ALL_DONE", null),
                Map.of("a", row("SUCCESS", null), "b", row("FAILED", null), "join", row("PENDING", null)));
        assertEquals(List.of("join"), decision.readyNodeIds());
    }

    /**
     * 前置还在 PENDING → 等待，不进就绪也不判失败
     */
    @Test
    void pendingDependencyWaits() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a"), null, null),
                Map.of("a", row("PENDING", null), "join", row("PENDING", null)));
        assertTrue(decision.readyNodeIds().isEmpty());
        assertTrue(decision.skipByFailure().isEmpty());
    }

    /**
     * 前置行缺失（循环体尚未展开）→ 本轮不参与判定
     */
    @Test
    void missingDependencySkips() {
        DagScheduler.Decision decision = decide(
                node("join", List.of("a", "ghost"), null, null),
                Map.of("a", row("SUCCESS", null), "join", row("PENDING", null)));
        assertTrue(decision.readyNodeIds().isEmpty());
        assertTrue(decision.skipByFailure().isEmpty());
    }

    /**
     * 终态判定：五个终态 + 两个中间态
     */
    @Test
    void terminalStatesAreComplete() {
        assertTrue(DagScheduler.isTerminal(row("SUCCESS", null)));
        assertTrue(DagScheduler.isTerminal(row("FAILED", null)));
        assertTrue(DagScheduler.isTerminal(row("DEAD", null)));
        assertTrue(DagScheduler.isTerminal(row("SKIPPED", "BRANCH")));
        assertTrue(DagScheduler.isTerminal(row("CANCELLED", null)));
        assertFalse(DagScheduler.isTerminal(row("PENDING", null)));
        assertFalse(DagScheduler.isTerminal(row("RUNNING", null)));
        assertFalse(DagScheduler.isTerminal(row("WAITING", null)));
    }

    /**
     * 满足判定：成功、BRANCH 裁剪、UPSTREAM 连带满足；FAILURE 裁剪不满足
     */
    @Test
    void satisfiedStatesHonourSkipReason() {
        assertTrue(DagScheduler.isSatisfied(row("SUCCESS", null)));
        assertTrue(DagScheduler.isSatisfied(row("SKIPPED", "BRANCH")));
        assertTrue(DagScheduler.isSatisfied(row("SKIPPED", "UPSTREAM")));
        assertFalse(DagScheduler.isSatisfied(row("SKIPPED", "FAILURE")));
        assertFalse(DagScheduler.isSatisfied(row("FAILED", null)));
    }

    /**
     * 失败策略：SKIP 裁剪，其余跟着失败
     */
    @Test
    void failurePolicyDeterminesSkip() {
        assertTrue(DagScheduler.shouldSkip("SKIP"));
        assertFalse(DagScheduler.shouldSkip("FAIL"));
    }

    // ==================== 工具 ====================

    /**
     * 造一个节点：dependsOn 固定，join 策略可空（走默认）
     */
    private static RuntimeNode node(String nodeId, List<String> dependsOn, String joinPolicy, String failurePolicy) {
        RuntimeNode node = new RuntimeNode();
        node.setNodeId(nodeId);
        node.setDependsOn(dependsOn);
        node.setJoinPolicy(joinPolicy);
        node.setJoinFailurePolicy(failurePolicy);
        return node;
    }

    /**
     * 造一行节点执行
     */
    private static FlowStepExecutionDO row(String status, String skipReason) {
        return FlowStepExecutionDO.builder()
                .nodeId("unused")
                .status(status)
                .skipReason(skipReason)
                .iterationNo(0)
                .attempt(1)
                .build();
    }

    /**
     * 单节点图的判定。graph 里只放 join 一个节点，前置行通过 latestByNode 直接给
     */
    private static DagScheduler.Decision decide(RuntimeNode join, Map<String, FlowStepExecutionDO> latestByNode) {
        PipelineGraph graph = new PipelineGraph();
        graph.getNodes().put(join.getNodeId(), join);
        return DagScheduler.decide(graph, latestByNode);
    }

}

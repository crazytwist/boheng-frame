package cn.boheng.frame.module.flow.enums;

/**
 * 节点被跳过的原因。汇合判定只把 BRANCH 视为满足，其余按失败策略处理。
 * 这是对参照项目「把 SKIPPED 无条件当成功」缺陷的修正。
 */
public enum SkippedReason {

    /**
     * 出口没被路由选中，整段子图被裁剪。汇合判定视为满足
     */
    BRANCH,

    /**
     * 前置失败，按 join_failure_policy=SKIP 被裁剪。汇合判定按失败处理
     */
    FAILURE,

    /**
     * 上游被裁剪连带本节点也进不了。汇合判定按失败处理
     */
    UPSTREAM

}

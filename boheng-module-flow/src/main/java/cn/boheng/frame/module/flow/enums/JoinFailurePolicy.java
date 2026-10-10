package cn.boheng.frame.module.flow.enums;

/**
 * 前置失败时本节点怎么办。和 {@link JoinPolicy} 配合使用。
 */
public enum JoinFailurePolicy {

    /**
     * 前置按汇合策略判为失败时，本节点跟着失败。默认
     */
    FAIL,

    /**
     * 前置失败时裁剪掉本节点，记为失败跳过，流程继续往别处走
     */
    SKIP

}

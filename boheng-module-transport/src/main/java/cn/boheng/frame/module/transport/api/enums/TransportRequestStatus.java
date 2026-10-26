package cn.boheng.frame.module.transport.api.enums;

/**
 * 运输需求生命周期状态。
 */
public enum TransportRequestStatus {
    /** 已创建。 */
    CREATED,
    /** 规划中。 */
    PLANNING,
    /** 已分配。 */
    ALLOCATED,
    /** 执行中。 */
    EXECUTING,
    /** 已完成。 */
    COMPLETED,
    /** 执行失败。 */
    FAILED,
    /** 已取消。 */
    CANCELLED
}

package cn.boheng.frame.module.transport.api.enums;

/**
 * Transport 执行器分配来源。
 */
public enum TransportAllocationMode {
    /** V1.0 本地规则分配。 */
    LOCAL,
    /** 未来由 Scheduler 生成方案。 */
    SCHEDULER
}

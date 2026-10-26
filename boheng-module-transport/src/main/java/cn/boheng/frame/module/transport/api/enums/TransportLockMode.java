package cn.boheng.frame.module.transport.api.enums;

/**
 * Transport 资源锁的实现来源。
 */
public enum TransportLockMode {
    /** V1.0 本地数据库锁。 */
    LOCAL,
    /** 未来 Resource 模块租约。 */
    RESOURCE
}

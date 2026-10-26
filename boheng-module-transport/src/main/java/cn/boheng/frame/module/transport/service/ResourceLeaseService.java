package cn.boheng.frame.module.transport.service;

/**
 * Transport 资源租约 SPI。V1 使用本地实现，未来可替换为 Resource 服务适配器。
 */
public interface ResourceLeaseService {

    /** 原子申请一个资源租约。资源已占用时返回 {@code null}。 */
    Lease acquire(Long orderId, String resourceType, String resourceKey, int leaseSeconds);

    /** 续租，只有当前锁令牌持有者可以续租。 */
    boolean renew(String lockToken, int leaseSeconds);

    /** 释放租约，只有当前锁令牌持有者可以释放。 */
    boolean release(String lockToken, String reason);

    /** 本地资源租约结果。 */
    class Lease {
        private final Long id;
        private final String lockToken;
        private final Long fencingToken;

        /** 创建租约结果。 */
        public Lease(Long id, String lockToken, Long fencingToken) {
            this.id = id;
            this.lockToken = lockToken;
            this.fencingToken = fencingToken;
        }

        /** 获取锁记录 ID。 */
        public Long getId() { return id; }
        /** 获取锁令牌。 */
        public String getLockToken() { return lockToken; }
        /** 获取防旧令牌。 */
        public Long getFencingToken() { return fencingToken; }
    }
}

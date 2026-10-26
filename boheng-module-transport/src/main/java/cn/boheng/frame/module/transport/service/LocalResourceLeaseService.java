package cn.boheng.frame.module.transport.service;

import cn.boheng.frame.module.transport.dal.dataobject.TransportLocalLockDO;
import cn.boheng.frame.module.transport.dal.mysql.TransportLocalLockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V1 本地资源租约实现。
 *
 * <p>通过数据库唯一键抢占资源，避免“先查询再插入”的并发窗口。</p>
 */
@Service
@RequiredArgsConstructor
public class LocalResourceLeaseService implements ResourceLeaseService {

    /** 本地锁 Mapper。 */
    private final TransportLocalLockMapper lockMapper;

    /**
     * 原子申请资源。唯一键冲突即视为资源繁忙。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Lease acquire(Long orderId, String resourceType, String resourceKey, int leaseSeconds) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        TransportLocalLockDO lock = TransportLocalLockDO.builder()
                .resourceType(resourceType).resourceKey(resourceKey).orderId(orderId)
                .lockToken(token).fencingToken(System.nanoTime())
                .status("ACTIVE").acquiredAt(now).heartbeatAt(now)
                .expireAt(now.plusSeconds(Math.max(1, leaseSeconds))).version(0).build();
        try {
            lockMapper.insert(lock);
            return new Lease(lock.getId(), lock.getLockToken(), lock.getFencingToken());
        } catch (DuplicateKeyException ex) {
            return null;
        }
    }

    /** 续租当前有效锁。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean renew(String lockToken, int leaseSeconds) {
        LocalDateTime now = LocalDateTime.now();
        return lockMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TransportLocalLockDO>()
                .eq(TransportLocalLockDO::getLockToken, lockToken)
                .eq(TransportLocalLockDO::getStatus, "ACTIVE")
                .set(TransportLocalLockDO::getHeartbeatAt, now)
                .set(TransportLocalLockDO::getExpireAt, now.plusSeconds(Math.max(1, leaseSeconds)))) > 0;
    }

    /** 释放当前有效锁。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(String lockToken, String reason) {
        return lockMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TransportLocalLockDO>()
                .eq(TransportLocalLockDO::getLockToken, lockToken)
                .eq(TransportLocalLockDO::getStatus, "ACTIVE")
                .set(TransportLocalLockDO::getStatus, "RELEASED")
                .set(TransportLocalLockDO::getReleasedAt, LocalDateTime.now())) > 0;
    }
}

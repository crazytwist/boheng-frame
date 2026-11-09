package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.tenant.core.context.TenantContextHolder;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W04/W05：Sweeper 跨租户扫描（ignore）+ 按行租户进入 expire（Proxy 桩，无 Mockito）。
 */
class MaterialReservationSweeperTest {

    private MaterialReservationSweeper sweeper;
    private final AtomicBoolean scanSawIgnore = new AtomicBoolean(false);
    private final List<Long> expireTenantIds = new ArrayList<>();
    private final AtomicBoolean expireSawIgnore = new AtomicBoolean(false);
    private final AtomicInteger expireCalls = new AtomicInteger();
    private final AtomicReference<List<MaterialInstanceDO>> scanResult = new AtomicReference<>(List.of());
    private final AtomicReference<RuntimeException> expireFailOnFirst = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        scanSawIgnore.set(false);
        expireTenantIds.clear();
        expireSawIgnore.set(false);
        expireCalls.set(0);
        scanResult.set(List.of());
        expireFailOnFirst.set(null);

        sweeper = new MaterialReservationSweeper();
        ReflectionTestUtils.setField(sweeper, "materialInstanceMapper", mapperProxy());
        ReflectionTestUtils.setField(sweeper, "materialInstanceService", serviceProxy());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void sweepScansIgnoringTenantAndExpiresUnderEachRowTenant() {
        scanResult.set(List.of(expiredRow(101L, 11L), expiredRow(102L, 22L)));

        sweeper.sweep();

        assertTrue(scanSawIgnore.get(), "跨租户扫描须 executeIgnore");
        assertEquals(List.of(11L, 22L), expireTenantIds);
        assertFalse(expireSawIgnore.get(), "逐租户释放时不得 ignore");
        assertEquals(2, expireCalls.get());
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    void sweepNoopWhenNoExpired() {
        scanResult.set(List.of());

        sweeper.sweep();

        assertEquals(0, expireCalls.get());
    }

    @Test
    void sweepContinuesWhenOneTenantFails() {
        scanResult.set(List.of(expiredRow(201L, 31L), expiredRow(202L, 32L)));
        expireFailOnFirst.set(new RuntimeException("tenant-31-boom"));

        sweeper.sweep();

        assertEquals(2, expireCalls.get());
        assertEquals(List.of(31L, 32L), expireTenantIds);
    }

    private MaterialInstanceMapper mapperProxy() {
        return (MaterialInstanceMapper) Proxy.newProxyInstance(
                MaterialInstanceMapper.class.getClassLoader(),
                new Class[]{MaterialInstanceMapper.class},
                (p, m, a) -> {
                    if ("toString".equals(m.getName())) {
                        return "MaterialInstanceMapperProxy";
                    }
                    if ("selectExpiredReserved".equals(m.getName())) {
                        scanSawIgnore.set(TenantContextHolder.isIgnore());
                        return scanResult.get();
                    }
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    private MaterialInstanceService serviceProxy() {
        return (MaterialInstanceService) Proxy.newProxyInstance(
                MaterialInstanceService.class.getClassLoader(),
                new Class[]{MaterialInstanceService.class},
                (p, m, a) -> {
                    if ("toString".equals(m.getName())) {
                        return "MaterialInstanceServiceProxy";
                    }
                    if ("expireReservations".equals(m.getName())) {
                        expireCalls.incrementAndGet();
                        expireTenantIds.add(TenantContextHolder.getTenantId());
                        if (TenantContextHolder.isIgnore()) {
                            expireSawIgnore.set(true);
                        }
                        RuntimeException fail = expireFailOnFirst.getAndSet(null);
                        if (fail != null) {
                            throw fail;
                        }
                        return 1;
                    }
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    private static MaterialInstanceDO expiredRow(long id, long tenantId) {
        MaterialInstanceDO row = new MaterialInstanceDO();
        row.setId(id);
        row.setTenantId(tenantId);
        row.setInstanceStatus("RESERVED");
        row.setReservationId("RSV-" + id);
        row.setReservedByExecutionId("EXE-" + id);
        row.setReservedUntil(LocalDateTime.now().minusMinutes(5));
        return row;
    }
}

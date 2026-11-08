package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 预留过期扫描器：跨租户读取候选，逐租户进入业务 Service 事务释放。
 */
@Component
@Slf4j
public class MaterialReservationSweeper {

    private static final long PERIOD_SECONDS = 30;
    private static final int BATCH_LIMIT = 100;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wms-reservation-sweeper");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private MaterialInstanceMapper materialInstanceMapper;

    @Resource
    private MaterialInstanceService materialInstanceService;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::sweep, 45, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    void sweep() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<MaterialInstanceDO> expired = TenantUtils.executeIgnore(
                    () -> materialInstanceMapper.selectExpiredReserved(now, BATCH_LIMIT));
            if (expired == null || expired.isEmpty()) {
                return;
            }
            int total = 0;
            for (MaterialInstanceDO row : expired) {
                try {
                    Integer n = TenantUtils.execute(row.getTenantId(),
                            () -> materialInstanceService.expireReservations(now, 1));
                    total += n == null ? 0 : n;
                } catch (Exception ex) {
                    log.warn("[wms][reservation-sweeper] 实例 {} 释放失败: {}", row.getId(), ex.getMessage());
                }
            }
            if (total > 0) {
                log.info("[wms][reservation-sweeper] 过期释放 {} 条", total);
            }
        } catch (Exception ex) {
            log.warn("[wms][reservation-sweeper] 扫描失败", ex);
        }
    }
}

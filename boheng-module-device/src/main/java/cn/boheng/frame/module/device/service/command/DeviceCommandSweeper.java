package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 收尾没人管的命令。
 *
 * 等回调、轮询都在发起请求的线程里等，服务重启后这些线程没了，命令会一直停在进行中。
 * 这里每分钟扫一次，过期的按超时或失败结束。设备占用锁本身带过期时间，不需要单独处理。
 */
@Component
@Slf4j
public class DeviceCommandSweeper {

    /**
     * 过了回调截止时刻再宽限多久才判超时，留给正在等待的线程自己处理
     */
    private static final long WAITING_GRACE_SEC = 30;

    /**
     * 没有截止时刻的命令，超过多久没结果算中断。轮询最长 10 分钟，这里多留一些
     */
    private static final long STALE_MINUTES = 15;

    /**
     * 扫描线程。守护线程，不拦应用退出
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "device-command-sweeper");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 命令表
     */
    @Resource
    private DeviceCommandMapper deviceCommandMapper;

    /**
     * 应用启动完成后开始定时扫描
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::sweep, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 应用关闭时停止扫描
     */
    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    /**
     * 扫一次。命令表按租户隔离，这里跨租户处理
     */
    void sweep() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int timedOut = TenantUtils.executeIgnore(
                    () -> deviceCommandMapper.timeoutExpiredWaiting(now.minusSeconds(WAITING_GRACE_SEC)));
            int failed = TenantUtils.executeIgnore(
                    () -> deviceCommandMapper.failStaleAcquired(now.minusMinutes(STALE_MINUTES)));
            if (timedOut + failed > 0) {
                log.info("[sweep][超时 {} 条，中断 {} 条]", timedOut, failed);
            }
        } catch (Exception ex) {
            log.warn("[sweep][收尾命令失败]", ex);
        }
    }

}

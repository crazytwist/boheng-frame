package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
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
 * 收尾没人管的命令。
 *
 * 回调命令只在数据库中等待，不占用请求线程。这里每分钟扫描一次，
 * 过期命令逐条收口并释放设备占用；其他中断命令按失败结束。
 */
@Component
@Slf4j
public class DeviceCommandSweeper {

    /**
     * 过了回调截止时刻再宽限多久才判超时，容纳callback和扫描边界的时间误差
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
     * 命令服务。逐条超时可以同时释放命令持有的设备占用
     */
    @Resource
    private DeviceCommandService deviceCommandService;

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
            List<DeviceCommandDO> expired = TenantUtils.executeIgnore(
                    () -> deviceCommandMapper.selectExpiredWaiting(now.minusSeconds(WAITING_GRACE_SEC)));
            int timedOut = 0;
            for (DeviceCommandDO command : expired) {
                TenantUtils.execute(command.getTenantId(), () -> deviceCommandService.timeout(
                        command.getId(), "超过回调截止时间仍未收到回调"));
                timedOut++;
            }
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

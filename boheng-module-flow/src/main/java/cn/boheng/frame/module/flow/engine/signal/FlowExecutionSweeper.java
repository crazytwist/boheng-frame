package cn.boheng.frame.module.flow.engine.signal;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 流程后台扫描器。每 20 秒一轮：
 * ① TIMER 等待到点 → 发定时信号唤醒；
 * ② WAITING 超过 deadline 的节点 → 按失败结算（重试或 DEAD）。
 * 不依赖外部定时组件；跨租户用 TenantUtils.executeIgnore（照设备域 Sweeper 的模式）。
 */
@Component
public class FlowExecutionSweeper {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionSweeper.class);

    /**
     * 扫描间隔（秒）
     */
    private static final long PERIOD_SECONDS = 20;

    /**
     * 运行表
     */
    @Resource
    private FlowExecutionMapper executionMapper;

    /**
     * 节点执行表
     */
    @Resource
    private FlowStepExecutionMapper stepExecutionMapper;

    /**
     * 信号分发
     */
    @Resource
    private FlowSignalDispatcher signalDispatcher;

    /**
     * 扫描线程
     */
    private ScheduledExecutorService scheduler;

    /**
     * 应用就绪后启动
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "flow-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sweep, 30, PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 停机
     */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 一轮扫描。异常吞掉，不让调度线程死掉
     */
    private void sweep() {
        try {
            TenantUtils.executeIgnore(this::scanWaitingSteps);
        } catch (Exception ex) {
            log.error("[flow][sweeper] 扫描异常", ex);
        }
    }

    /**
     * 扫全部进行中运行的等待节点
     */
    private void scanWaitingSteps() {
        List<FlowExecutionDO> running = executionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowExecutionDO>()
                        .eq(FlowExecutionDO::getStatus, "RUNNING"));
        for (FlowExecutionDO execution : running) {
            try {
                TenantUtils.execute(execution.getTenantId(), () -> scanOne(execution));
            } catch (Exception ex) {
                log.warn("[flow][sweeper] 运行 {} 扫描失败: {}", execution.getId(), ex.getMessage());
            }
        }
    }

    /**
     * 扫一个运行的等待节点：TIMER 到点唤醒；超时按失败结算
     */
    private void scanOne(FlowExecutionDO execution) {
        List<FlowStepExecutionDO> waiting = stepExecutionMapper.selectWaiting(execution.getId());
        LocalDateTime now = LocalDateTime.now();
        for (FlowStepExecutionDO row : waiting) {
            if (row.getDeadlineAt() == null) {
                continue; // MANUAL 等人，没有期限
            }
            if (!now.isBefore(row.getDeadlineAt())) {
                if ("TIMER".equals(row.getWaitingFor())) {
                    signalDispatcher.timerSignal(execution.getId(), row.getNodeId(), row.getAttempt());
                } else if ("DEVICE_CALLBACK".equals(row.getWaitingFor())) {
                    signalDispatcher.deviceTaskTimeout(execution.getId(), row.getNodeId(), row.getAttempt());
                }
            }
        }
    }

}

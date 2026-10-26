package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.engine.type.device.DeviceCommandStepType;
import cn.boheng.frame.module.flow.framework.log.FlowLogContextHolder;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.ExecutionStatus;

/**
 * 节点执行的工作线程池。设备命令是阻塞调用（设备域内部等回调），
 * 绝不能挂在 HTTP 线程或引擎推进线程上——这里用固定守护线程池承接，
 * 并在任务里显式带上租户上下文（照设备域 DeviceCommandSweeper 的模式）。
 */
@Component
public class FlowExecutionWorker {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionWorker.class);

    /**
     * 并行设备的并发度。一期固定，后续可配置化
     */
    private static final int POOL_SIZE = 8;

    /**
     * 运行表（任务里重新加载，不能用主线程的引用）
     */
    @Resource
    private FlowExecutionMapper executionMapper;

    /**
     * 设备步骤执行器（阻塞调用设备契约）
     */
    @Resource
    private DeviceCommandStepType deviceCommandStepType;

    /**
     * 引擎（结算时回写）。用 @Lazy 语义由 Spring 代理处理，避免构造期循环依赖
     */
    @Resource
    private FlowExecutionEngine engine;

    /**
     * 工作线程池
     */
    private ExecutorService executor;

    /**
     * 应用就绪后建池
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        executor = Executors.newFixedThreadPool(POOL_SIZE, runnable -> {
            Thread thread = new Thread(runnable, "flow-step-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 停机时等在途任务收口
     */
    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 提交一个设备步骤。线程里带租户上下文执行并结算
     */
    public void submitDeviceStep(cn.boheng.frame.module.flow.engine.execution.PipelineGraph graph,
                                 FlowExecutionDO execution, FlowStepExecutionDO row,
                                 cn.boheng.frame.module.flow.engine.execution.RuntimeNode node) {
        Long tenantId = execution.getTenantId();
        Long executionId = execution.getId();
        String executionNo = execution.getExecutionNo();
        String nodeId = row.getNodeId();
        executor.submit(() -> TenantUtils.execute(tenantId, () -> {
            // 工作线程显式带执行/节点维度，设备命令日志才能与运行串起来
            FlowLogContextHolder.setExecutionNo(executionNo);
            FlowLogContextHolder.setNodeId(nodeId);
            try {
                FlowExecutionDO fresh = executionMapper.selectById(executionId);
                if (fresh == null || !ExecutionStatus.RUNNING.name().equals(fresh.getStatus())) {
                    return;
                }
                log.info("[flow] 设备任务下发 node={} iter={} attempt={}",
                        nodeId, row.getIterationNo(), row.getAttempt());
                StepOutcome outcome = engine.executeStep(graph, fresh, row, node);
                engine.settle(row, outcome);
            } catch (RuntimeException ex) {
                log.error("[flow] 设备步骤执行异常: node={}", nodeId, ex);
                engine.settle(row, StepOutcome.failed("执行异常: " + ex.getMessage()));
            } finally {
                FlowLogContextHolder.clear();
            }
        }));
    }

}

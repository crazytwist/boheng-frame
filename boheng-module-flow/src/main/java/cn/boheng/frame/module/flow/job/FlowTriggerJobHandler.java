package cn.boheng.frame.module.flow.job;

import cn.boheng.frame.framework.quartz.core.handler.JobHandler;
import cn.boheng.frame.module.flow.service.execution.FlowExecutionService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 定时触发流程的 JobHandler。配合系统定时任务（infra_job + cron）使用：
 * 在任务参数里放一段 JSON，指定要启动的流程版本、启动入参和租户，到点后由调度器调 {@link #execute(String)}。
 *
 * <p>参数 JSON 结构：
 * <pre>{@code
 * {
 *   "tenantId": 1,              // 必填，租户编号
 *   "definitionId": 100,        // 必填，流程版本主键（须已发布）
 *   "inputParams": "{\"a\":1}", // 可空，启动入参 JSON 字符串
 *   "businessKey": "daily-001"  // 可空，业务关联键
 * }
 * }</pre>
 *
 * <p>注意：这里不走 {@code @TenantJob} 的「逐个租户执行」，因为租户是任务参数显式指定的，
 * 由 {@link FlowExecutionService#startScheduled} 内部用租户上下文执行，避免一条任务串跑所有租户。
 */
@Component
public class FlowTriggerJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(FlowTriggerJobHandler.class);

    /**
     * 流程运行服务。定时启动复用已发布的校验与引擎启动逻辑
     */
    @Resource
    private FlowExecutionService flowExecutionService;

    @Override
    public String execute(String param) throws Exception {
        if (StrUtil.isBlank(param)) {
            throw new IllegalArgumentException("定时触发流程的任务参数不能为空，需包含 tenantId 与 definitionId");
        }
        JSONObject json = JSONUtil.parseObj(param);
        Long tenantId = json.getLong("tenantId");
        Long definitionId = json.getLong("definitionId");
        if (tenantId == null || definitionId == null) {
            throw new IllegalArgumentException("定时触发流程缺少 tenantId 或 definitionId");
        }
        String inputParams = json.getStr("inputParams");
        String businessKey = json.getStr("businessKey");
        Long executionId = flowExecutionService.startScheduled(definitionId, inputParams, businessKey, tenantId);
        log.info("[flow][job] 定时触发流程 definitionId={} tenantId={} -> executionId={}",
                definitionId, tenantId, executionId);
        return "运行编号：" + executionId;
    }

}

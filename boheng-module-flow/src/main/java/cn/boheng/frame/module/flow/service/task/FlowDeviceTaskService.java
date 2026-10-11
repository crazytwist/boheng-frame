package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskEventDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskEventMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskMapper;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备异步任务队列服务。任务不推给设备，而是入库等机器端拉取：
 * <p>节点入队（PENDING）→ 机器端认领（CLAIMED）→ 回报（DONE/FAILED）。
 * 机器端回报 / 超时的信号结算在 {@code FlowSignalDispatcher}，这里只管任务表与事件流水。
 */
@Service
@Validated
public class FlowDeviceTaskService {

    /**
     * 事件类型
     */
    private static final String EVENT_CREATED = "CREATED";
    private static final String EVENT_CLAIMED = "CLAIMED";
    private static final String EVENT_DONE = "DONE";
    private static final String EVENT_FAILED = "FAILED";

    /**
     * 任务状态
     */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CLAIMED = "CLAIMED";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 任务表
     */
    @Resource
    private FlowDeviceTaskMapper deviceTaskMapper;

    /**
     * 事件流水表
     */
    @Resource
    private FlowDeviceTaskEventMapper eventMapper;

    /**
     * 入队一条任务
     *
     * @return 对外任务号
     */
    public String enqueue(Long executionId, String nodeId, Integer attempt, Integer iterationNo,
                          String deviceCode, String actionCode, Map<String, Object> params) {
        String taskNo = "task_" + IdUtil.getSnowflakeNextIdStr();
        FlowDeviceTaskDO task = FlowDeviceTaskDO.builder()
                .taskNo(taskNo)
                .callbackToken(IdUtil.fastSimpleUUID())
                .executionId(executionId)
                .nodeId(nodeId)
                .attempt(attempt)
                .iterationNo(iterationNo)
                .deviceCode(deviceCode)
                .actionCode(actionCode)
                .paramsJson(params == null || params.isEmpty() ? null : JSONUtil.toJsonStr(params))
                .status(STATUS_PENDING)
                .build();
        deviceTaskMapper.insert(task);
        writeEvent(task, null, STATUS_PENDING, EVENT_CREATED, null, null);
        return taskNo;
    }

    /**
     * 机器端认领一条待执行任务。deviceCode / actionCode 空则不筛，
     * 机器端可按设备、按任务类型、或组合拉取。CAS 认领防多机重复执行。
     *
     * @return 认领到的任务（含 callbackToken，回报时带回），没有则返回 null
     */
    public FlowDeviceTaskDO claim(String deviceCode, String actionCode, String workerId) {
        for (int i = 0; i < 3; i++) {
            FlowDeviceTaskDO task = deviceTaskMapper.selectClaimable(deviceCode, actionCode);
            if (task == null) {
                return null;
            }
            Long waitMs = task.getCreateTime() == null ? null
                    : Duration.between(task.getCreateTime(), LocalDateTime.now()).toMillis();
            if (deviceTaskMapper.claim(task.getId(), workerId, waitMs) == 1) {
                FlowDeviceTaskDO claimed = deviceTaskMapper.selectById(task.getId());
                writeEvent(claimed, STATUS_PENDING, STATUS_CLAIMED, EVENT_CLAIMED, workerId, null);
                return claimed;
            }
            // 认领失败 = 被别的机器端抢走，重试再取一条
        }
        return null;
    }

    /**
     * 机器端回报：任务推进到终态（DONE / FAILED）
     */
    public FlowDeviceTaskDO finish(String taskNo, boolean success, String resultJson, String errorMessage) {
        FlowDeviceTaskDO task = deviceTaskMapper.selectByTaskNo(taskNo);
        if (task == null) {
            return null;
        }
        Long executeMs = task.getClaimedAt() == null ? null
                : Duration.between(task.getClaimedAt(), LocalDateTime.now()).toMillis();
        deviceTaskMapper.finish(task.getId(), success ? STATUS_DONE : STATUS_FAILED,
                resultJson, errorMessage, executeMs);
        FlowDeviceTaskDO finished = deviceTaskMapper.selectById(task.getId());
        writeEvent(finished, task.getStatus(), finished.getStatus(),
                success ? EVENT_DONE : EVENT_FAILED, finished.getClaimedBy(), resultJson);
        return finished;
    }

    /**
     * 超时标记：把仍在 PENDING / CLAIMED 的任务置 FAILED
     */
    public void timeout(Long executionId, String nodeId, Integer attempt) {
        FlowDeviceTaskDO task = deviceTaskMapper.selectByExecutionNode(executionId, nodeId, attempt);
        if (task == null) {
            return;
        }
        if (STATUS_PENDING.equals(task.getStatus()) || STATUS_CLAIMED.equals(task.getStatus())) {
            String fromStatus = task.getStatus();
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage("设备任务超时");
            if (task.getClaimedAt() != null) {
                task.setExecuteMs(Duration.between(task.getClaimedAt(), LocalDateTime.now()).toMillis());
            }
            deviceTaskMapper.updateById(task);
            writeEvent(task, fromStatus, STATUS_FAILED, EVENT_FAILED, task.getClaimedBy(), "{\"timeout\":true}");
        }
    }

    /**
     * 回报令牌校验。常量时间比较，防时序攻击。
     */
    public boolean matchesToken(FlowDeviceTaskDO task, String callbackToken) {
        if (task == null || StrUtil.isBlank(task.getCallbackToken()) || StrUtil.isBlank(callbackToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                task.getCallbackToken().getBytes(StandardCharsets.UTF_8),
                callbackToken.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 按任务号查
     */
    public FlowDeviceTaskDO selectByTaskNo(String taskNo) {
        return StrUtil.isBlank(taskNo) ? null : deviceTaskMapper.selectByTaskNo(taskNo);
    }

    /**
     * 写一行事件流水
     */
    private void writeEvent(FlowDeviceTaskDO task, String fromStatus, String toStatus,
                            String eventType, String claimedBy, String payload) {
        eventMapper.insert(FlowDeviceTaskEventDO.builder()
                .taskNo(task.getTaskNo())
                .eventType(eventType)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .claimedBy(claimedBy)
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .build());
    }

}

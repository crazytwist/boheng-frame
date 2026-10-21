package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.device.api.DeviceApi;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.engine.signal.FlowSignalDispatcher;
import cn.boheng.frame.module.flow.enums.external.ExternalTaskStatusEnum;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskEventDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskEventMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskMapper;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEVICE_TASK_EXTERNAL_STATUS_INVALID;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEVICE_TASK_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEVICE_TASK_SIGNAL_REJECTED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEVICE_TASK_STATUS_INVALID;

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
     * 设备契约。认领时反查设备能力集（设备编码 → 类型 → 启用动作）
     */
    @Resource
    private DeviceApi deviceApi;

    @Resource
    @Lazy
    private FlowSignalDispatcher signalDispatcher;

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
     * 机器端认领一条待执行任务。deviceCode 必传（声明要操作哪台设备），
     * 平台按它的设备类型反查能力集，匹配「不限设备 + 能力集内类型」的任务；
     * actionCode 可选，进一步限定只要某一种任务。CAS 认领防多机重复执行。
     *
     * @return 认领到的任务（含 callbackToken，回报时带回），没有则返回 null
     */
    public FlowDeviceTaskDO claim(String deviceCode, String actionCode, String workerId) {
        // 反查设备能力集。设备不存在或没有启用动作时无任务可认领
        List<String> actionCodes = deviceApi.listActionCodesByDevice(deviceCode);
        if (actionCodes.isEmpty()) {
            return null;
        }
        // 可选限定任务类型：非空时要求它在本设备能力集内，否则这台设备不会做，拒绝认领
        if (StrUtil.isNotBlank(actionCode)) {
            if (!actionCodes.contains(actionCode)) {
                return null;
            }
            actionCodes = List.of(actionCode);
        }
        for (int i = 0; i < 3; i++) {
            FlowDeviceTaskDO task = deviceTaskMapper.selectClaimable(deviceCode, actionCodes);
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
     * 机器端回报：只从 CLAIMED 推进到终态。没抢到返回 false，调用方不要再结算步骤。
     */
    public boolean finish(String taskNo, boolean success, String resultJson, String errorMessage) {
        FlowDeviceTaskDO task = deviceTaskMapper.selectByTaskNo(taskNo);
        if (task == null) {
            return false;
        }
        Long executeMs = task.getClaimedAt() == null ? null
                : Duration.between(task.getClaimedAt(), LocalDateTime.now()).toMillis();
        String toStatus = success ? STATUS_DONE : STATUS_FAILED;
        if (deviceTaskMapper.finish(task.getId(), toStatus, resultJson, errorMessage, executeMs) != 1) {
            return false;
        }
        writeEvent(task, task.getStatus(), toStatus,
                success ? EVENT_DONE : EVENT_FAILED, task.getClaimedBy(), resultJson);
        return true;
    }

    /**
     * 超时标记：只从 PENDING / CLAIMED 置为 FAILED。没抢到返回 false。
     */
    public boolean timeout(Long executionId, String nodeId, Integer attempt) {
        FlowDeviceTaskDO task = deviceTaskMapper.selectByExecutionNode(executionId, nodeId, attempt);
        if (task == null) {
            return false;
        }
        if (!STATUS_PENDING.equals(task.getStatus()) && !STATUS_CLAIMED.equals(task.getStatus())) {
            return false;
        }
        Long executeMs = task.getClaimedAt() == null ? null
                : Duration.between(task.getClaimedAt(), LocalDateTime.now()).toMillis();
        String fromStatus = task.getStatus();
        if (deviceTaskMapper.timeoutIfOpen(task.getId(), executeMs) != 1) {
            return false;
        }
        writeEvent(task, fromStatus, STATUS_FAILED, EVENT_FAILED, task.getClaimedBy(), "{\"timeout\":true}");
        return true;
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
     * 按区域（工位）查待执行任务。area 对应 {@code device_code}，只返回 {@code PENDING}。
     */
    public List<FlowDeviceTaskDO> listPendingByArea(String area) {
        if (StrUtil.isBlank(area)) {
            return List.of();
        }
        return deviceTaskMapper.selectListPendingByDeviceCode(area.trim());
    }

    /**
     * 清华 {@code updateTaskStatus}：按对外 taskId 更新状态。
     * <ul>
     *   <li>{@link ExternalTaskStatusEnum#SS_Running} → 认领（PENDING→CLAIMED，等同 claim）</li>
     *   <li>{@link ExternalTaskStatusEnum#SS_Finished} → 成功完成并推进流程</li>
     *   <li>{@link ExternalTaskStatusEnum#SS_Error} → 失败完成</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatusByExternalTaskId(Integer taskId, Integer statusCode) {
        if (taskId == null || statusCode == null) {
            throw exception(FLOW_DEVICE_TASK_EXTERNAL_STATUS_INVALID);
        }
        final ExternalTaskStatusEnum externalStatus;
        try {
            externalStatus = ExternalTaskStatusEnum.fromCode(statusCode);
        } catch (IllegalArgumentException e) {
            throw exception(FLOW_DEVICE_TASK_EXTERNAL_STATUS_INVALID);
        }
        FlowDeviceTaskDO task = requireByExternalTaskId(taskId);
        switch (externalStatus) {
            case SS_Running -> claimKnownTask(task, null);
            case SS_Finished -> finishKnownTask(task, true, "{}", null);
            case SS_Error -> finishKnownTask(task, false, null, "设备任务执行错误");
            case SS_Idle, SS_Pause, SS_Wait, SS_Occupy -> {
                // 与清华一致仅更新业务态；队列态无对应项时不抛错
            }
        }
    }

    /**
     * 按 listTaskByArea 返回的 taskId 查任务。
     */
    public FlowDeviceTaskDO requireByExternalTaskId(int externalTaskId) {
        FlowDeviceTaskDO task = deviceTaskMapper.selectByExternalTaskId(externalTaskId);
        if (task == null || !FlowDeviceTaskExternalIds.matches(task, externalTaskId)) {
            throw exception(FLOW_DEVICE_TASK_NOT_EXISTS);
        }
        return task;
    }

    /**
     * 认领指定任务行（非按设备扫描队列）。
     */
    public FlowDeviceTaskDO claimKnownTask(FlowDeviceTaskDO task, String workerId) {
        if (STATUS_CLAIMED.equals(task.getStatus())) {
            return deviceTaskMapper.selectById(task.getId());
        }
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw exception(FLOW_DEVICE_TASK_STATUS_INVALID);
        }
        String claimedBy = StrUtil.blankToDefault(StrUtil.trim(workerId),
                StrUtil.blankToDefault(task.getDeviceCode(), "external"));
        Long waitMs = task.getCreateTime() == null ? null
                : Duration.between(task.getCreateTime(), LocalDateTime.now()).toMillis();
        if (deviceTaskMapper.claim(task.getId(), claimedBy, waitMs) != 1) {
            throw exception(FLOW_DEVICE_TASK_STATUS_INVALID);
        }
        FlowDeviceTaskDO claimed = deviceTaskMapper.selectById(task.getId());
        writeEvent(claimed, STATUS_PENDING, STATUS_CLAIMED, EVENT_CLAIMED, claimedBy, null);
        return claimed;
    }

    private void finishKnownTask(FlowDeviceTaskDO task, boolean success, String resultJson, String errorMessage) {
        FlowDeviceTaskDO current = deviceTaskMapper.selectById(task.getId());
        if (STATUS_PENDING.equals(current.getStatus())) {
            current = claimKnownTask(current, null);
        }
        if (!STATUS_CLAIMED.equals(current.getStatus())) {
            if (STATUS_DONE.equals(current.getStatus()) || STATUS_FAILED.equals(current.getStatus())) {
                return;
            }
            throw exception(FLOW_DEVICE_TASK_STATUS_INVALID);
        }
        boolean accepted = signalDispatcher.deviceTaskSignalTrusted(
                current.getTaskNo(), success, resultJson, errorMessage);
        if (!accepted) {
            throw exception(FLOW_DEVICE_TASK_SIGNAL_REJECTED);
        }
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

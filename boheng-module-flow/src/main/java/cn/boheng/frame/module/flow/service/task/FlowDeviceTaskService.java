package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskMapper;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * 设备异步任务队列服务。任务不推给设备，而是入库等机器端拉取：
 * <p>节点入队（PENDING）→ 机器端认领（CLAIMED）→ 回报（DONE/FAILED）。
 * 机器端回报 / 超时的信号结算在 {@code FlowSignalDispatcher}，这里只管任务表。
 */
@Service
@Validated
public class FlowDeviceTaskService {

    /**
     * 任务表
     */
    @Resource
    private FlowDeviceTaskMapper deviceTaskMapper;

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
                .executionId(executionId)
                .nodeId(nodeId)
                .attempt(attempt)
                .iterationNo(iterationNo)
                .deviceCode(deviceCode)
                .actionCode(actionCode)
                .paramsJson(params == null || params.isEmpty() ? null : JSONUtil.toJsonStr(params))
                .status("PENDING")
                .build();
        deviceTaskMapper.insert(task);
        return taskNo;
    }

    /**
     * 机器端认领一条待执行任务。deviceCode / actionCode 空则不筛，
     * 机器端可按设备、按任务类型、或组合拉取。CAS 认领防多机重复执行。
     *
     * @return 认领到的任务，没有则返回 null
     */
    public FlowDeviceTaskDO claim(String deviceCode, String actionCode, String workerId) {
        for (int i = 0; i < 3; i++) {
            FlowDeviceTaskDO task = deviceTaskMapper.selectClaimable(deviceCode, actionCode);
            if (task == null) {
                return null;
            }
            if (deviceTaskMapper.claim(task.getId(), workerId) == 1) {
                return deviceTaskMapper.selectById(task.getId());
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
        deviceTaskMapper.finish(task.getId(), success ? "DONE" : "FAILED", resultJson, errorMessage);
        return deviceTaskMapper.selectById(task.getId());
    }

    /**
     * 超时标记：把仍在 PENDING / CLAIMED 的任务置 FAILED
     */
    public void timeout(Long executionId, String nodeId, Integer attempt) {
        FlowDeviceTaskDO task = deviceTaskMapper.selectByExecutionNode(executionId, nodeId, attempt);
        if (task == null) {
            return;
        }
        if ("PENDING".equals(task.getStatus()) || "CLAIMED".equals(task.getStatus())) {
            task.setStatus("FAILED");
            task.setErrorMessage("设备任务超时");
            deviceTaskMapper.updateById(task);
        }
    }

    /**
     * 任务是否还有效（未达终态）。取消运行时把 PENDING 置 CANCELLED 用
     */
    public FlowDeviceTaskDO selectByTaskNo(String taskNo) {
        return StrUtil.isBlank(taskNo) ? null : deviceTaskMapper.selectByTaskNo(taskNo);
    }

}

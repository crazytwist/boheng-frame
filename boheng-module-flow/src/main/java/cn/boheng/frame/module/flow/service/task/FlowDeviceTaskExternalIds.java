package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * {@code listTaskByArea} 返回的 {@code taskId} 与 {@code flow_device_task} 互查规则（与组装侧一致）。
 */
public final class FlowDeviceTaskExternalIds {

    private FlowDeviceTaskExternalIds() {
    }

    public static int toExternalTaskId(FlowDeviceTaskDO task) {
        if (task == null) {
            throw new IllegalArgumentException("task is null");
        }
        return toExternalTaskId(task.getId(), task.getParamsJson());
    }

    public static int toExternalTaskId(Long id, String paramsJson) {
        JSONObject params = StrUtil.isBlank(paramsJson) ? new JSONObject() : JSONUtil.parseObj(paramsJson);
        Integer explicit = params.getInt("externalTaskId");
        if (explicit != null) {
            return explicit;
        }
        if (id == null) {
            throw new IllegalArgumentException("task id is null");
        }
        long raw = id;
        if (raw > 0 && raw <= Integer.MAX_VALUE) {
            return (int) raw;
        }
        return (int) (raw & 0x7FFFFFFFL);
    }

    public static boolean matches(FlowDeviceTaskDO task, int externalTaskId) {
        return task != null && toExternalTaskId(task) == externalTaskId;
    }

}

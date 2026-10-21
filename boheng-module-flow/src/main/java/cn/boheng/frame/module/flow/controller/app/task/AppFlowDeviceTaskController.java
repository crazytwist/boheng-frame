package cn.boheng.frame.module.flow.controller.app.task;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskClaimReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskUpdateStatusReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskCompleteReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskListByAreaReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.ExternalTaskDomainRespVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.engine.signal.FlowSignalDispatcher;
import cn.boheng.frame.module.flow.service.task.FlowDeviceTaskExternalService;
import cn.boheng.frame.module.flow.service.task.FlowDeviceTaskService;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 机器端设备任务接口（/app-api 前缀）。
 *
 * <p>设备任务不「推」给设备，而是「存」在队列里由机器端主动拉取：认领一条待执行任务，
 * 执行完回报结果，回报触发的信号结算在 {@link FlowSignalDispatcher} 里推进流程。
 * 机器端不登录，靠 {@code tenant-id} 请求头限定租户（与后台前端同款头）。
 */
@Tag(name = "机器端 - 流程设备任务")
@RestController
@RequestMapping("/flow/device-task")
@Validated
public class AppFlowDeviceTaskController {

    /**
     * 任务队列服务
     */
    @Resource
    private FlowDeviceTaskService deviceTaskService;

    /**
     * 清华 {@code ExternalTaskDomain} 组装（与 {@code TsinghuaFlowController#listTaskByArea} 一致：单条或 null）。
     */
    @Resource
    private FlowDeviceTaskExternalService deviceTaskExternalService;

    /**
     * 信号分发。回报走它结算节点
     */
    @Resource
    private FlowSignalDispatcher signalDispatcher;

    /**
     * 按区域查询可执行任务（清华 {@code TsinghuaFlowController#listTaskByArea} / {@code POST /external/task/executable}）。
     * 请求 {@code Area} → {@code device_code}；仅 {@code PENDING}；响应为 {@code ExternalTaskDomain} 结构，无任务时 data 为 null。
     */
    @PostMapping("/listTaskByArea")
    @PermitAll
    @Operation(summary = "按区域查询可执行任务（ExternalTaskDomain）",
            description = "area 对应 flow_device_task.device_code；只查 PENDING；taskType/name 为当前流程节点 sortNo（序号）；靠 tenant-id 请求头限定租户")
    public CommonResult<ExternalTaskDomainRespVO> listTaskByArea(
            @Valid @RequestBody FlowDeviceTaskListByAreaReqVO reqVO) {
        return success(deviceTaskExternalService.listFirstExecutableByArea(reqVO.getArea()));
    }

    /**
     * 按 taskId 更新任务状态（清华 {@code TsinghuaFlowController#updateTaskStatus}）。
     * taskId 为 {@link #listTaskByArea} 返回的 taskId；Status 为 ExternalTaskStatusEnum 的 code（1=运行中/认领，2=完成）。
     */
    @PostMapping("/task/status")
    @PermitAll
    @Operation(summary = "按 taskId 更新任务状态",
            description = "TaskId + Status（PascalCase）；SS_Running(1) 认领，SS_Finished(2) 完成并推进流程；靠 tenant-id 限定租户")
    public CommonResult<Boolean> updateTaskStatus(@Valid @RequestBody FlowDeviceTaskUpdateStatusReqVO reqVO) {
        deviceTaskService.updateStatusByExternalTaskId(reqVO.getTaskId(), reqVO.getStatus());
        return success(true);
    }

    /**
     * 认领一条待执行任务。没有可执行任务时返回空 data。
     */
    @PostMapping("/claim")
    @PermitAll
    @Operation(summary = "认领一条待执行任务",
            description = "deviceCode 必传（声明要操作哪台设备），平台按设备能力集匹配任务；"
                    + "actionCode 可选限定类型；workerId 可选，缺省与 deviceCode 相同；靠 tenant-id 请求头限定租户")
    public CommonResult<FlowDeviceTaskRespVO> claim(@Valid @RequestBody FlowDeviceTaskClaimReqVO reqVO) {
        String workerId = StrUtil.blankToDefault(StrUtil.trim(reqVO.getWorkerId()), reqVO.getDeviceCode());
        FlowDeviceTaskDO task = deviceTaskService.claim(reqVO.getDeviceCode(), reqVO.getActionCode(), workerId);
        return success(task == null ? null : BeanUtils.toBean(task, FlowDeviceTaskRespVO.class));
    }

    /**
     * 回报任务执行结果。accepted=false 表示任务已终态或节点已不在等待（重复回报 / 超时），
     * 机器端可据此判断要不要重发。
     */
    @PostMapping("/complete")
    @PermitAll
    @Operation(summary = "回报任务执行结果")
    public CommonResult<Boolean> complete(@Valid @RequestBody FlowDeviceTaskCompleteReqVO reqVO) {
        boolean accepted = signalDispatcher.deviceTaskSignal(reqVO.getTaskNo(), reqVO.getCallbackToken(),
                Boolean.TRUE.equals(reqVO.getSuccess()), reqVO.getResultJson(), reqVO.getErrorMessage());
        return success(accepted);
    }


}

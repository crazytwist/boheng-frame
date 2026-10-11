package cn.boheng.frame.module.flow.controller.app.task;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskClaimReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskCompleteReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.engine.signal.FlowSignalDispatcher;
import cn.boheng.frame.module.flow.service.task.FlowDeviceTaskService;
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
public class FlowDeviceTaskController {

    /**
     * 任务队列服务
     */
    @Resource
    private FlowDeviceTaskService deviceTaskService;

    /**
     * 信号分发。回报走它结算节点
     */
    @Resource
    private FlowSignalDispatcher signalDispatcher;

    /**
     * 认领一条待执行任务。没有可执行任务时返回空 data。
     */
    @PostMapping("/claim")
    @PermitAll
    @Operation(summary = "认领一条待执行任务",
            description = "按设备 / 任务类型（均可空，可组合）拉取，靠 tenant-id 请求头限定租户")
    public CommonResult<FlowDeviceTaskRespVO> claim(@Valid @RequestBody FlowDeviceTaskClaimReqVO reqVO) {
        FlowDeviceTaskDO task = deviceTaskService.claim(reqVO.getDeviceCode(), reqVO.getActionCode(),
                reqVO.getWorkerId());
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

package cn.boheng.frame.module.flow.controller.admin.execution;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowSignalManualReqVO;
import cn.boheng.frame.module.flow.service.execution.FlowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 流程信号。人工确认 / 拒绝等待人工的节点。
 * 设备回调与定时到点由内部链路触发，不对外暴露。
 */
@Tag(name = "管理后台 - 流程信号")
@RestController
@RequestMapping("/flow/signal")
@Validated
public class FlowSignalController {

    /**
     * 运行服务
     */
    @Resource
    private FlowExecutionService flowExecutionService;

    @PostMapping("/manual")
    @Operation(summary = "人工确认或拒绝节点")
    @PreAuthorize("@ss.hasPermission('flow:execution:signal')")
    public CommonResult<Boolean> manualConfirm(@Valid @RequestBody FlowSignalManualReqVO reqVO) {
        flowExecutionService.manualConfirm(reqVO);
        return success(true);
    }

}

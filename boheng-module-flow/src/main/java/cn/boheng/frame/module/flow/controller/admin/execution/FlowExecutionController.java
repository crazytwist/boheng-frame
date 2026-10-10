package cn.boheng.frame.module.flow.controller.admin.execution;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionRespVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionStartReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionRespVO;
import cn.boheng.frame.module.flow.service.execution.FlowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 流程运行。启动、取消、运行列表和节点时间线。
 */
@Tag(name = "管理后台 - 流程运行")
@RestController
@RequestMapping("/flow/execution")
@Validated
public class FlowExecutionController {

    /**
     * 运行服务
     */
    @Resource
    private FlowExecutionService flowExecutionService;

    @PostMapping("/start")
    @Operation(summary = "启动一次运行")
    @PreAuthorize("@ss.hasPermission('flow:execution:create')")
    public CommonResult<Long> startExecution(@Valid @RequestBody FlowExecutionStartReqVO reqVO) {
        return success(flowExecutionService.startExecution(reqVO));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消进行中的运行")
    @Parameter(name = "id", description = "运行编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:execution:update')")
    public CommonResult<Boolean> cancelExecution(@RequestParam("id") Long id) {
        flowExecutionService.cancelExecution(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得运行详情（含节点时间线与分支决策）")
    @Parameter(name = "id", description = "运行编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:execution:query')")
    public CommonResult<FlowExecutionRespVO> getExecution(@RequestParam("id") Long id) {
        return success(flowExecutionService.getExecution(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得运行分页")
    @PreAuthorize("@ss.hasPermission('flow:execution:query')")
    public CommonResult<PageResult<FlowExecutionRespVO>> getExecutionPage(@Valid FlowExecutionPageReqVO reqVO) {
        return success(flowExecutionService.getExecutionPage(reqVO));
    }

    @GetMapping("/step-page")
    @Operation(summary = "获得节点执行分页（时间线）")
    @PreAuthorize("@ss.hasPermission('flow:execution:query')")
    public CommonResult<PageResult<FlowStepExecutionRespVO>> getStepExecutionPage(@Valid FlowStepExecutionPageReqVO reqVO) {
        return success(flowExecutionService.getStepExecutionPage(reqVO));
    }

}

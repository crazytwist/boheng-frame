package cn.boheng.frame.module.flow.controller.admin.step;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepRespVO;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepSaveReqVO;
import cn.boheng.frame.module.flow.service.step.FlowStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 实验步骤。和流程分开维护，关联在流程步骤里完成。
 */
@Tag(name = "管理后台 - 实验步骤")
@RestController
@RequestMapping("/flow/step")
@Validated
public class FlowStepController {

    /**
     * 步骤服务
     */
    @Resource
    private FlowStepService flowStepService;

    @PostMapping("/create")
    @Operation(summary = "创建实验步骤")
    @PreAuthorize("@ss.hasPermission('flow:step:create')")
    public CommonResult<Long> createStep(@Valid @RequestBody FlowStepSaveReqVO reqVO) {
        return success(flowStepService.createStep(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新实验步骤")
    @PreAuthorize("@ss.hasPermission('flow:step:update')")
    public CommonResult<Boolean> updateStep(@Valid @RequestBody FlowStepSaveReqVO reqVO) {
        flowStepService.updateStep(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除实验步骤")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:step:delete')")
    public CommonResult<Boolean> deleteStep(@RequestParam("id") Long id) {
        flowStepService.deleteStep(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得实验步骤")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:step:query')")
    public CommonResult<FlowStepRespVO> getStep(@RequestParam("id") Long id) {
        return success(flowStepService.getStep(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得实验步骤分页")
    @PreAuthorize("@ss.hasPermission('flow:step:query')")
    public CommonResult<PageResult<FlowStepRespVO>> getStepPage(@Valid FlowStepPageReqVO reqVO) {
        return success(flowStepService.getStepPage(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得启用的实验步骤")
    @PreAuthorize("@ss.hasPermission('flow:step:query')")
    public CommonResult<List<FlowStepRespVO>> getEnabledStepList() {
        return success(flowStepService.getEnabledStepList());
    }

}

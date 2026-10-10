package cn.boheng.frame.module.flow.controller.admin.relation;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationRespVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationSaveReqVO;
import cn.boheng.frame.module.flow.service.relation.FlowRelationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 流程和步骤的关联。步骤本身在步骤接口里维护。
 */
@Tag(name = "管理后台 - 流程步骤关联")
@RestController
@RequestMapping("/flow/relation")
@Validated
public class FlowRelationController {

    /**
     * 关联服务
     */
    @Resource
    private FlowRelationService flowRelationService;

    @PostMapping("/create")
    @Operation(summary = "把步骤关联到流程")
    @PreAuthorize("@ss.hasPermission('flow:relation:create')")
    public CommonResult<Long> createRelation(@Valid @RequestBody FlowRelationSaveReqVO reqVO) {
        return success(flowRelationService.createRelation(reqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "解除流程和步骤的关联")
    @Parameter(name = "id", description = "关联编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:relation:delete')")
    public CommonResult<Boolean> deleteRelation(@RequestParam("id") Long id) {
        flowRelationService.deleteRelation(id);
        return success(true);
    }

    @PostMapping("/move")
    @Operation(summary = "调整关联顺序")
    @Parameter(name = "id", description = "关联编号", required = true)
    @Parameter(name = "offset", description = "-1 上移，1 下移", required = true)
    @PreAuthorize("@ss.hasPermission('flow:relation:update')")
    public CommonResult<Boolean> moveRelation(@RequestParam("id") Long id, @RequestParam("offset") Integer offset) {
        flowRelationService.moveRelation(id, offset);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得流程步骤分页")
    @PreAuthorize("@ss.hasPermission('flow:relation:query')")
    public CommonResult<PageResult<FlowRelationRespVO>> getRelationPage(@Valid FlowRelationPageReqVO reqVO) {
        return success(flowRelationService.getRelationPage(reqVO));
    }

}

package cn.boheng.frame.module.flow.controller.admin.definition;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionCreateReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionRespVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionSaveReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowIssueRespVO;
import cn.boheng.frame.module.flow.service.definition.FlowDefinitionService;
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
 * 实验流程的定义。发布和修订从这里进。步骤和关联是另外的接口。
 */
@Tag(name = "管理后台 - 实验流程定义")
@RestController
@RequestMapping("/flow/definition")
@Validated
public class FlowDefinitionController {

    /**
     * 流程定义服务
     */
    @Resource
    private FlowDefinitionService flowDefinitionService;

    @PostMapping("/create")
    @Operation(summary = "创建流程草稿")
    @PreAuthorize("@ss.hasPermission('flow:definition:create')")
    public CommonResult<Long> createDefinition(@Valid @RequestBody FlowDefinitionCreateReqVO reqVO) {
        return success(flowDefinitionService.createDefinition(reqVO));
    }

    @PutMapping("/save")
    @Operation(summary = "保存流程草稿")
    @PreAuthorize("@ss.hasPermission('flow:definition:update')")
    public CommonResult<Boolean> saveDefinition(@Valid @RequestBody FlowDefinitionSaveReqVO reqVO) {
        flowDefinitionService.saveDefinition(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除流程草稿")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:definition:delete')")
    public CommonResult<Boolean> deleteDefinition(@RequestParam("id") Long id) {
        flowDefinitionService.deleteDefinition(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得流程")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:definition:query')")
    public CommonResult<FlowDefinitionRespVO> getDefinition(@RequestParam("id") Long id) {
        return success(flowDefinitionService.getDefinition(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得流程分页")
    @PreAuthorize("@ss.hasPermission('flow:definition:query')")
    public CommonResult<PageResult<FlowDefinitionRespVO>> getDefinitionPage(@Valid FlowDefinitionPageReqVO reqVO) {
        return success(flowDefinitionService.getDefinitionPage(reqVO));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得流程下拉列表")
    @PreAuthorize("@ss.hasPermission('flow:definition:query')")
    public CommonResult<List<FlowDefinitionRespVO>> getDefinitionSimpleList() {
        return success(flowDefinitionService.getDefinitionSimpleList());
    }

    @PostMapping("/validate")
    @Operation(summary = "检查流程能否发布")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:definition:query')")
    public CommonResult<List<FlowIssueRespVO>> validateDefinition(@RequestParam("id") Long id) {
        return success(flowDefinitionService.validateDefinition(id));
    }

    @PostMapping("/publish")
    @Operation(summary = "发布流程")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:definition:publish')")
    public CommonResult<Boolean> publishDefinition(@RequestParam("id") Long id) {
        flowDefinitionService.publishDefinition(id);
        return success(true);
    }

    @PostMapping("/retire")
    @Operation(summary = "停用流程")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:definition:publish')")
    public CommonResult<Boolean> retireDefinition(@RequestParam("id") Long id) {
        flowDefinitionService.retireDefinition(id);
        return success(true);
    }

    @PostMapping("/revise")
    @Operation(summary = "修订出下一版草稿")
    @Parameter(name = "id", description = "来源版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('flow:definition:create')")
    public CommonResult<Long> reviseDefinition(@RequestParam("id") Long id) {
        return success(flowDefinitionService.reviseDefinition(id));
    }

}

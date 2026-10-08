package cn.boheng.frame.module.wms.controller.admin.movement;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementRespVO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 物料流水 Controller
 *
 * ⚠️ 流水只增不改：仅提供查询接口，不提供创建 / 修改 / 删除入口。
 * 流水由物料实例的上下架、转移、消耗等业务操作在同一事务内写入。
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 物料流水")
@RestController
@RequestMapping("/wms/material-movement")
@Validated
public class MaterialMovementController {

    @Resource
    private MaterialMovementService materialMovementService;


    @GetMapping("/page")
    @Operation(summary = "获得物料流水分页")
    @PreAuthorize("@ss.hasPermission('wms:movement:query')")
    public CommonResult<PageResult<MaterialMovementRespVO>> getMovementPage(@Valid MaterialMovementPageReqVO pageReqVO) {
        PageResult<MaterialMovementDO> pageResult = materialMovementService.getMovementPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MaterialMovementRespVO.class));
    }

    @GetMapping("/list-by-instance")
    @Operation(summary = "获得某实例的完整轨迹（正序时间线）")
    @Parameter(name = "instanceId", description = "实例编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:movement:query')")
    public CommonResult<List<MaterialMovementRespVO>> getMovementListByInstance(
            @RequestParam("instanceId") Long instanceId) {
        List<MaterialMovementDO> list = materialMovementService.getMovementListByInstance(instanceId);
        return success(BeanUtils.toBean(list, MaterialMovementRespVO.class));
    }

    @GetMapping("/list-by-root-instance")
    @Operation(summary = "获得某顶层容器整树的流水")
    @Parameter(name = "rootInstanceId", description = "根实例编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:movement:query')")
    public CommonResult<List<MaterialMovementRespVO>> getMovementListByRootInstance(
            @RequestParam("rootInstanceId") Long rootInstanceId) {
        List<MaterialMovementDO> list = materialMovementService.getMovementListByRootInstance(rootInstanceId);
        return success(BeanUtils.toBean(list, MaterialMovementRespVO.class));
    }

    @GetMapping("/list-by-slot")
    @Operation(summary = "获得某槽位的最近流水（源或目标任一命中）")
    @Parameter(name = "slotId", description = "槽位编号", required = true, example = "2048")
    @Parameter(name = "limit", description = "条数，默认 10", example = "10")
    @PreAuthorize("@ss.hasPermission('wms:movement:query')")
    public CommonResult<List<MaterialMovementRespVO>> getMovementListBySlot(
            @RequestParam("slotId") Long slotId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<MaterialMovementDO> list = materialMovementService.getRecentMovementListBySlot(slotId, limit);
        return success(BeanUtils.toBean(list, MaterialMovementRespVO.class));
    }
}

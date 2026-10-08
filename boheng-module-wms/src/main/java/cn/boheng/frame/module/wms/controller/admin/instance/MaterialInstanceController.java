package cn.boheng.frame.module.wms.controller.admin.instance;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeByContainerReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceSaveReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTakeOutReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTransferReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.service.instance.MaterialInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 物料实例 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 物料实例")
@RestController
@RequestMapping("/wms/material-instance")
@Validated
public class MaterialInstanceController {

    @Resource
    private MaterialInstanceService materialInstanceService;


    @PostMapping("/create")
    @Operation(summary = "创建物料实例")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:create')")
    public CommonResult<Long> createMaterialInstance(@Valid @RequestBody MaterialInstanceSaveReqVO createReqVO) {
        return success(materialInstanceService.createMaterialInstance(createReqVO));
    }


    @PostMapping("/create-batch")
    @Operation(summary = "批量创建物料实例（同一父实例下批量生成子实例）")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:create')")
    public CommonResult<List<Long>> createMaterialInstanceBatch(@Valid @RequestBody List<MaterialInstanceSaveReqVO> createReqVOs) {
        return success(materialInstanceService.createMaterialInstanceBatch(createReqVOs));
    }


    @PutMapping("/update")
    @Operation(summary = "更新物料实例")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:update')")
    public CommonResult<Boolean> updateMaterialInstance(@Valid @RequestBody MaterialInstanceSaveReqVO updateReqVO) {
        materialInstanceService.updateMaterialInstance(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除物料实例")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:delete')")
    public CommonResult<Boolean> deleteMaterialInstance(@RequestParam("id") Long id) {
        materialInstanceService.deleteMaterialInstance(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除物料实例")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('wms:material-instance:delete')")
    public CommonResult<Boolean> deleteMaterialInstanceList(@RequestParam("ids") List<Long> ids) {
        materialInstanceService.deleteMaterialInstanceList(ids);
        return success(true);
    }


    @GetMapping("/page")
    @Operation(summary = "获得物料实例分页")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:query')")
    public CommonResult<PageResult<MaterialInstanceRespVO>> getMaterialInstancePage(@Valid MaterialInstancePageReqVO pageReqVO) {
        PageResult<MaterialInstanceDO> pageResult = materialInstanceService.getMaterialInstancePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MaterialInstanceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得物料实例")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:query')")
    public CommonResult<MaterialInstanceRespVO> getMaterialInstance(@RequestParam("id") Long id) {
        MaterialInstanceDO materialInstance = materialInstanceService.getMaterialInstance(id);
        return success(BeanUtils.toBean(materialInstance, MaterialInstanceRespVO.class));
    }


    @GetMapping("/list")
    @Operation(summary = "获得物料实例列表")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:query')")
    public CommonResult<List<MaterialInstanceRespVO>> getMaterialInstanceList() {
        List<MaterialInstanceDO> list = materialInstanceService.getMaterialInstanceList();
        return success(BeanUtils.toBean(list, MaterialInstanceRespVO.class));
    }

    @GetMapping("/list-unplaced")
    @Operation(summary = "获得未落位的顶层实例列表（供上架弹窗选择源）")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:query')")
    public CommonResult<List<MaterialInstanceRespVO>> getUnplacedList() {
        List<MaterialInstanceDO> list = materialInstanceService.getUnplacedList();
        return success(BeanUtils.toBean(list, MaterialInstanceRespVO.class));
    }


    // ==================== 落位操作（上架 / 下架 / 转移） ====================

    @PostMapping("/put-in")
    @Operation(summary = "上架：把顶层实例放到目标槽位")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:put-in')")
    public CommonResult<Long> putIn(@Valid @RequestBody MaterialInstancePutInReqVO reqVO) {
        return success(materialInstanceService.putIn(reqVO));
    }

    @PostMapping("/take-out")
    @Operation(summary = "下架：把在架实例从槽位取下")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:take-out')")
    public CommonResult<Long> takeOut(@Valid @RequestBody MaterialInstanceTakeOutReqVO reqVO) {
        return success(materialInstanceService.takeOut(reqVO));
    }

    @PostMapping("/transfer")
    @Operation(summary = "转移：把在架实例从当前槽位挪到目标槽位")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:transfer')")
    public CommonResult<Long> transfer(@Valid @RequestBody MaterialInstanceTransferReqVO reqVO) {
        return success(materialInstanceService.transfer(reqVO));
    }


    // ==================== 消耗（供其他模块调用） ====================

    @PostMapping("/consume")
    @Operation(summary = "消耗：按实例精确消耗（支持离散数量与连续体积，可一次多个实例）")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:consume')")
    public CommonResult<List<Long>> consume(@Valid @RequestBody MaterialInstanceConsumeReqVO reqVO) {
        return success(materialInstanceService.consume(reqVO));
    }

    @PostMapping("/consume-by-container")
    @Operation(summary = "消耗：按容器领取 N 个子实例（调用方无需知道具体是哪几个）")
    @PreAuthorize("@ss.hasPermission('wms:material-instance:consume')")
    public CommonResult<List<Long>> consumeByContainer(@Valid @RequestBody MaterialInstanceConsumeByContainerReqVO reqVO) {
        return success(materialInstanceService.consumeByContainer(reqVO));
    }
}

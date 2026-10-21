package cn.boheng.frame.module.wms.controller.app;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialIdQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialItem;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialLiquidTypeQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPosQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPositionAreaQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPositionAreaRespVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialStatusTypeQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialUpdateCmdReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.TipConsumeReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.TipResourceRespVO;
import cn.boheng.frame.module.wms.service.app.AppMaterialInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - 物料实例")
@RestController
@RequestMapping("/wms/material-instance")
@Validated
@Slf4j
public class AppMaterialInstanceController {

    @Resource
    private AppMaterialInstanceService appMaterialInstanceService;

    @PostMapping("/query")
    @PermitAll
    @Operation(summary = "根据位置信息查询实验室物料")
    public CommonResult<List<MaterialItem>> queryByPos(@Valid @RequestBody MaterialPosQueryReqVO query) {
        List<MaterialItem> items = appMaterialInstanceService.listByPos(query);
        return success(items);
    }

    @PostMapping("/query-by-liquid-type")
    @PermitAll
    @Operation(summary = "根据液体类型和物料类型查询物料信息")
    public CommonResult<List<MaterialItem>> queryByLiquidType(@Valid @RequestBody MaterialLiquidTypeQueryReqVO query) {
        List<MaterialItem> items = appMaterialInstanceService.listByLiquidAndType(query);
        return success(items);
    }

    @PostMapping("/query-by-status-and-type")
    @PermitAll
    @Operation(summary = "根据状态和类型查询物料信息")
    public CommonResult<List<MaterialItem>> queryByStatusAndType(@Valid @RequestBody MaterialStatusTypeQueryReqVO query) {
        List<MaterialItem> items = appMaterialInstanceService.listByStatusAndType(query);
        return success(items);
    }

    @PostMapping("/query-by-id")
    @PermitAll
    @Operation(summary = "根据物料 ID 查询物料信息")
    public CommonResult<List<MaterialItem>> queryById(@Valid @RequestBody MaterialIdQueryReqVO query) {
        List<MaterialItem> items = appMaterialInstanceService.listByIds(query);
        return success(items);
    }

    @PostMapping("/status")
    @PermitAll
    @Operation(summary = "设置物料当前状态（更新当前位置、容器内液量、物料状态）")
    public CommonResult<Boolean> updateLabware(@Valid @RequestBody List<MaterialUpdateCmdReqVO> updateCmdList) {
        return success(appMaterialInstanceService.updateLabware(updateCmdList));
    }

    @PostMapping("/query-position-by-area")
    @PermitAll
    @Operation(summary = "按区域查询可用位置",
            description = "器具类型匹配槽位 suitable_materials 中 kind=CONTAINER 的 materialCode；无父级区域树，PositionArea 为 zone_code")
    public CommonResult<MaterialPositionAreaRespVO> queryPositionByArea(
            @Valid @RequestBody MaterialPositionAreaQueryReqVO areaQuery) {
        MaterialPositionAreaRespVO resp = appMaterialInstanceService.queryPositionByArea(areaQuery);
        return success(resp);
    }

    @PostMapping("/tip/consume")
    @PermitAll
    @Operation(summary = "Tip 头消耗")
    public CommonResult<Boolean> tipConsume(@Valid @RequestBody TipConsumeReqVO tipConsumeCmd) {
        return success(appMaterialInstanceService.tipConsume(tipConsumeCmd));
    }

    @GetMapping("/tip/resource")
    @PermitAll
    @Operation(summary = "查询 Tip 头资源")
    public CommonResult<TipResourceRespVO> queryTipResource(@RequestParam("areaCode") String areaCode) {
        return success(appMaterialInstanceService.queryTipResource(areaCode));
    }

    @PostMapping("/tip/complement")
    @PermitAll
    @Operation(summary = "Tip 头补齐")
    public CommonResult<Boolean> tipComplement(@RequestParam("tipId") String tipId) {
        return success(appMaterialInstanceService.tipComplement(tipId));
    }
}

package cn.boheng.frame.module.wms.controller.admin.zone;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZonePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZoneRespVO;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZoneSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.zone.ZoneDO;
import cn.boheng.frame.module.wms.service.zone.ZoneService;
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
 * 区域树 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 区域树")
@RestController
@RequestMapping("/wms/zone")
@Validated
public class ZoneController {

    @Resource
    private ZoneService zoneService;


    @PostMapping("/create")
    @Operation(summary = "创建区域树")
    @PreAuthorize("@ss.hasPermission('wms:zone:create')")
    public CommonResult<Long> createZone(@Valid @RequestBody ZoneSaveReqVO createReqVO) {
        return success(zoneService.createZone(createReqVO));
    }


    @PutMapping("/update")
    @Operation(summary = "更新区域树")
    @PreAuthorize("@ss.hasPermission('wms:zone:update')")
    public CommonResult<Boolean> updateZone(@Valid @RequestBody ZoneSaveReqVO updateReqVO) {
        zoneService.updateZone(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除区域树")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:zone:delete')")
    public CommonResult<Boolean> deleteZone(@RequestParam("id") Long id) {
        zoneService.deleteZone(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除区域树")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('wms:zone:delete')")
    public CommonResult<Boolean> deleteZoneList(@RequestParam("ids") List<Long> ids) {
        zoneService.deleteZoneList(ids);
        return success(true);
    }


    @GetMapping("/page")
    @Operation(summary = "获得区域树分页")
    @PreAuthorize("@ss.hasPermission('wms:zone:query')")
    public CommonResult<PageResult<ZoneRespVO>> getZonePage(@Valid ZonePageReqVO pageReqVO) {
        PageResult<ZoneDO> pageResult = zoneService.getZonePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ZoneRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得区域树")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:zone:query')")
    public CommonResult<ZoneRespVO> getZone(@RequestParam("id") Long id) {
        ZoneDO zone = zoneService.getZone(id);
        return success(BeanUtils.toBean(zone, ZoneRespVO.class));
    }


    @GetMapping("/list")
    @Operation(summary = "获得区域树列表")
    @PreAuthorize("@ss.hasPermission('wms:zone:query')")
    public CommonResult<List<ZoneRespVO>> getZoneList() {
        List<ZoneDO> list = zoneService.getZoneList();
        return success(BeanUtils.toBean(list, ZoneRespVO.class));
    }
}

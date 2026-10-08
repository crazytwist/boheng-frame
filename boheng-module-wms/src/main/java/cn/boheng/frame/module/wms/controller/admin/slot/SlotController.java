package cn.boheng.frame.module.wms.controller.admin.slot;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotRespVO;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.service.slot.SlotService;
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
 * 槽位 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 槽位")
@RestController
@RequestMapping("/wms/slot")
@Validated
public class SlotController {

    @Resource
    private SlotService slotService;


    @PostMapping("/create")
    @Operation(summary = "创建槽位")
    @PreAuthorize("@ss.hasPermission('wms:slot:create')")
    public CommonResult<Long> createSlot(@Valid @RequestBody SlotSaveReqVO createReqVO) {
        return success(slotService.createSlot(createReqVO));
    }


    @PutMapping("/update")
    @Operation(summary = "更新槽位")
    @PreAuthorize("@ss.hasPermission('wms:slot:update')")
    public CommonResult<Boolean> updateSlot(@Valid @RequestBody SlotSaveReqVO updateReqVO) {
        slotService.updateSlot(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除槽位")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:slot:delete')")
    public CommonResult<Boolean> deleteSlot(@RequestParam("id") Long id) {
        slotService.deleteSlot(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除槽位")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('wms:slot:delete')")
    public CommonResult<Boolean> deleteSlotList(@RequestParam("ids") List<Long> ids) {
        slotService.deleteSlotList(ids);
        return success(true);
    }


    @GetMapping("/page")
    @Operation(summary = "获得槽位分页")
    @PreAuthorize("@ss.hasPermission('wms:slot:query')")
    public CommonResult<PageResult<SlotRespVO>> getSlotPage(@Valid SlotPageReqVO pageReqVO) {
        PageResult<SlotDO> pageResult = slotService.getSlotPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SlotRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得槽位")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:slot:query')")
    public CommonResult<SlotRespVO> getSlot(@RequestParam("id") Long id) {
        SlotDO slot = slotService.getSlot(id);
        return success(BeanUtils.toBean(slot, SlotRespVO.class));
    }


    @GetMapping("/list")
    @Operation(summary = "获得槽位列表")
    @Parameter(name = "zoneCode", description = "区域编码（可空，空则查全部）", required = false, example = "ZONE_RACK_01")
    @PreAuthorize("@ss.hasPermission('wms:slot:query')")
    public CommonResult<List<SlotRespVO>> getSlotList(@RequestParam(value = "zoneCode", required = false) String zoneCode) {
        List<SlotDO> list = slotService.getSlotList(zoneCode);
        return success(BeanUtils.toBean(list, SlotRespVO.class));
    }

    @PostMapping("/generate-by-zone")
    @Operation(summary = "按区域布局批量生成槽位")
    @Parameter(name = "zoneCode", description = "区域编码（须为 RACK 类型且已配置货架尺寸）", required = true, example = "ZONE_RACK_01")
    @PreAuthorize("@ss.hasPermission('wms:slot:create')")
    public CommonResult<Integer> generateSlotsByZone(@RequestParam("zoneCode") String zoneCode) {
        return success(slotService.generateSlotsByZone(zoneCode));
    }
}

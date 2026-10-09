package cn.boheng.frame.module.device.controller.admin.paramset;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetPageReqVO;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetRespVO;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.paramset.DeviceParamSetDO;
import cn.boheng.frame.module.device.service.paramset.DeviceParamSetService;
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
import static cn.boheng.frame.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 参数集预设 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 参数集预设")
@RestController
@RequestMapping("/device/param-set")
@Validated
public class DeviceParamSetController {
    /**
     * 参数集服务
     */

    @Resource
    private DeviceParamSetService deviceParamSetService;

    @PostMapping("/create")
    @Operation(summary = "创建参数集")
    @PreAuthorize("@ss.hasPermission('device:param-set:create')")
    public CommonResult<Long> createParamSet(@Valid @RequestBody DeviceParamSetSaveReqVO createReqVO) {
        return success(deviceParamSetService.createParamSet(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新参数集")
    @PreAuthorize("@ss.hasPermission('device:param-set:update')")
    public CommonResult<Boolean> updateParamSet(@Valid @RequestBody DeviceParamSetSaveReqVO updateReqVO) {
        deviceParamSetService.updateParamSet(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-validated")
    @Operation(summary = "标记参数集验证状态")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @Parameter(name = "validated", description = "是否已验证", required = true, example = "true")
    @PreAuthorize("@ss.hasPermission('device:param-set:update')")
    public CommonResult<Boolean> updateValidated(@RequestParam("id") Long id,
                                                 @RequestParam("validated") Boolean validated) {
        deviceParamSetService.updateValidated(id, validated, String.valueOf(getLoginUserId()));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除参数集")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:param-set:delete')")
    public CommonResult<Boolean> deleteParamSet(@RequestParam("id") Long id) {
        deviceParamSetService.deleteParamSet(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除参数集")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('device:param-set:delete')")
    public CommonResult<Boolean> deleteParamSetList(@RequestParam("ids") List<Long> ids) {
        deviceParamSetService.deleteParamSetList(ids);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得参数集分页")
    @PreAuthorize("@ss.hasPermission('device:param-set:query')")
    public CommonResult<PageResult<DeviceParamSetRespVO>> getParamSetPage(@Valid DeviceParamSetPageReqVO pageReqVO) {
        PageResult<DeviceParamSetDO> pageResult = deviceParamSetService.getParamSetPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DeviceParamSetRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得参数集")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:param-set:query')")
    public CommonResult<DeviceParamSetRespVO> getParamSet(@RequestParam("id") Long id) {
        DeviceParamSetDO paramSet = deviceParamSetService.getParamSet(id);
        return success(BeanUtils.toBean(paramSet, DeviceParamSetRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得参数集列表（可按设备类型 + 动作过滤）")
    @Parameter(name = "deviceTypeCode", description = "设备类型编码", example = "PLATE_READER")
    @Parameter(name = "actionCode", description = "动作编码", example = "READ_PLATE")
    @PreAuthorize("@ss.hasPermission('device:param-set:query')")
    public CommonResult<List<DeviceParamSetRespVO>> getParamSetList(
            @RequestParam(value = "deviceTypeCode", required = false) String deviceTypeCode,
            @RequestParam(value = "actionCode", required = false) String actionCode) {
        List<DeviceParamSetDO> list = deviceParamSetService.getParamSetList(deviceTypeCode, actionCode);
        return success(BeanUtils.toBean(list, DeviceParamSetRespVO.class));
    }

}

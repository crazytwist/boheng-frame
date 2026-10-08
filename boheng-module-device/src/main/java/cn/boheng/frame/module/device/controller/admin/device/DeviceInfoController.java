package cn.boheng.frame.module.device.controller.admin.device;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
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
 * 设备台账 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 设备台账")
@RestController
@RequestMapping("/device/info")
@Validated
public class DeviceInfoController {

    @Resource
    private DeviceInfoService deviceInfoService;

    @PostMapping("/create")
    @Operation(summary = "创建设备台账")
    @PreAuthorize("@ss.hasPermission('device:info:create')")
    public CommonResult<Long> createDevice(@Valid @RequestBody DeviceSaveReqVO createReqVO) {
        return success(deviceInfoService.createDevice(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备台账")
    @PreAuthorize("@ss.hasPermission('device:info:update')")
    public CommonResult<Boolean> updateDevice(@Valid @RequestBody DeviceSaveReqVO updateReqVO) {
        deviceInfoService.updateDevice(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备台账")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:info:delete')")
    public CommonResult<Boolean> deleteDevice(@RequestParam("id") Long id) {
        deviceInfoService.deleteDevice(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除设备台账")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('device:info:delete')")
    public CommonResult<Boolean> deleteDeviceList(@RequestParam("ids") List<Long> ids) {
        deviceInfoService.deleteDeviceList(ids);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得设备台账分页")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<PageResult<DeviceRespVO>> getDevicePage(@Valid DevicePageReqVO pageReqVO) {
        PageResult<DeviceInfoDO> pageResult = deviceInfoService.getDevicePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DeviceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得设备台账")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<DeviceRespVO> getDevice(@RequestParam("id") Long id) {
        DeviceInfoDO device = deviceInfoService.getDevice(id);
        return success(BeanUtils.toBean(device, DeviceRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得设备台账列表")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<List<DeviceRespVO>> getDeviceList() {
        List<DeviceInfoDO> list = deviceInfoService.getDeviceList();
        return success(BeanUtils.toBean(list, DeviceRespVO.class));
    }

}

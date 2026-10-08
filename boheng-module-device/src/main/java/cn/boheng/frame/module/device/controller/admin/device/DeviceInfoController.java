package cn.boheng.frame.module.device.controller.admin.device;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePortraitRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
import cn.boheng.frame.module.device.service.http.DeviceHttpService;
import cn.hutool.core.util.StrUtil;
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
    @Resource
    private DeviceHttpService deviceHttpService;

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

    @PostMapping("/invoke")
    @Operation(summary = "调用设备 HTTP 动作。已配置登录时先登录，token 失效后重新登录再试一次")
    @PreAuthorize("@ss.hasPermission('device:info:update')")
    public CommonResult<DeviceInvokeRespVO> invoke(@Valid @RequestBody DeviceInvokeReqVO reqVO) {
        return success(deviceHttpService.invoke(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "获得设备台账分页")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<PageResult<DeviceRespVO>> getDevicePage(@Valid DevicePageReqVO pageReqVO) {
        PageResult<DeviceInfoDO> pageResult = deviceInfoService.getDevicePage(pageReqVO);
        PageResult<DeviceRespVO> result = BeanUtils.toBean(pageResult, DeviceRespVO.class);
        result.getList().forEach(this::maskLogin);
        return success(result);
    }

    @GetMapping("/portrait")
    @Operation(summary = "获得设备台账画像")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<DevicePortraitRespVO> getDevicePortrait(@RequestParam("id") Long id) {
        return success(deviceInfoService.getDevicePortrait(id));
    }

    @GetMapping("/get")
    @Operation(summary = "获得设备台账")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<DeviceRespVO> getDevice(@RequestParam("id") Long id) {
        DeviceInfoDO device = deviceInfoService.getDevice(id);
        DeviceRespVO resp = BeanUtils.toBean(device, DeviceRespVO.class);
        maskLogin(resp);
        return success(resp);
    }

    @GetMapping("/list")
    @Operation(summary = "获得设备台账列表")
    @PreAuthorize("@ss.hasPermission('device:info:query')")
    public CommonResult<List<DeviceRespVO>> getDeviceList() {
        List<DeviceInfoDO> list = deviceInfoService.getDeviceList();
        List<DeviceRespVO> result = BeanUtils.toBean(list, DeviceRespVO.class);
        result.forEach(this::maskLogin);
        return success(result);
    }

    private void maskLogin(DeviceRespVO device) {
        if (device == null) {
            return;
        }
        device.setLoginConfigured(StrUtil.isNotBlank(device.getLoginPassword()));
        device.setLoginPassword(null);
    }

}

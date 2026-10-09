package cn.boheng.frame.module.device.controller.admin.property;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertyPageReqVO;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertyRespVO;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertySaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.property.DevicePropertyDO;
import cn.boheng.frame.module.device.service.property.DevicePropertyService;
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
 * 设备可观测属性定义 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 设备可观测属性定义")
@RestController
@RequestMapping("/device/property")
@Validated
public class DevicePropertyController {
    /**
     * 设备属性服务
     */

    @Resource
    private DevicePropertyService devicePropertyService;

    @PostMapping("/create")
    @Operation(summary = "创建设备属性")
    @PreAuthorize("@ss.hasPermission('device:property:create')")
    public CommonResult<Long> createProperty(@Valid @RequestBody DevicePropertySaveReqVO createReqVO) {
        return success(devicePropertyService.createProperty(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备属性")
    @PreAuthorize("@ss.hasPermission('device:property:update')")
    public CommonResult<Boolean> updateProperty(@Valid @RequestBody DevicePropertySaveReqVO updateReqVO) {
        devicePropertyService.updateProperty(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备属性")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:property:delete')")
    public CommonResult<Boolean> deleteProperty(@RequestParam("id") Long id) {
        devicePropertyService.deleteProperty(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除设备属性")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('device:property:delete')")
    public CommonResult<Boolean> deletePropertyList(@RequestParam("ids") List<Long> ids) {
        devicePropertyService.deletePropertyList(ids);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得设备属性分页")
    @PreAuthorize("@ss.hasPermission('device:property:query')")
    public CommonResult<PageResult<DevicePropertyRespVO>> getPropertyPage(@Valid DevicePropertyPageReqVO pageReqVO) {
        PageResult<DevicePropertyDO> pageResult = devicePropertyService.getPropertyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DevicePropertyRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得设备属性")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:property:query')")
    public CommonResult<DevicePropertyRespVO> getProperty(@RequestParam("id") Long id) {
        DevicePropertyDO property = devicePropertyService.getProperty(id);
        return success(BeanUtils.toBean(property, DevicePropertyRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得设备属性列表（可按设备类型过滤）")
    @Parameter(name = "deviceTypeCode", description = "设备类型编码", example = "PLATE_READER")
    @PreAuthorize("@ss.hasPermission('device:property:query')")
    public CommonResult<List<DevicePropertyRespVO>> getPropertyList(@RequestParam(value = "deviceTypeCode", required = false) String deviceTypeCode) {
        List<DevicePropertyDO> list = devicePropertyService.getPropertyList(deviceTypeCode);
        return success(BeanUtils.toBean(list, DevicePropertyRespVO.class));
    }

}

package cn.boheng.frame.module.device.controller.admin.action;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionPageReqVO;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionRespVO;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.service.action.DeviceActionService;
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
 * 设备动作定义 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 设备动作定义")
@RestController
@RequestMapping("/device/action")
@Validated
public class DeviceActionController {

    @Resource
    private DeviceActionService deviceActionService;

    @PostMapping("/create")
    @Operation(summary = "创建设备动作")
    @PreAuthorize("@ss.hasPermission('device:action:create')")
    public CommonResult<Long> createAction(@Valid @RequestBody DeviceActionSaveReqVO createReqVO) {
        return success(deviceActionService.createAction(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备动作")
    @PreAuthorize("@ss.hasPermission('device:action:update')")
    public CommonResult<Boolean> updateAction(@Valid @RequestBody DeviceActionSaveReqVO updateReqVO) {
        deviceActionService.updateAction(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备动作")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:action:delete')")
    public CommonResult<Boolean> deleteAction(@RequestParam("id") Long id) {
        deviceActionService.deleteAction(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除设备动作")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('device:action:delete')")
    public CommonResult<Boolean> deleteActionList(@RequestParam("ids") List<Long> ids) {
        deviceActionService.deleteActionList(ids);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得设备动作分页")
    @PreAuthorize("@ss.hasPermission('device:action:query')")
    public CommonResult<PageResult<DeviceActionRespVO>> getActionPage(@Valid DeviceActionPageReqVO pageReqVO) {
        PageResult<DeviceActionDO> pageResult = deviceActionService.getActionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DeviceActionRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得设备动作")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('device:action:query')")
    public CommonResult<DeviceActionRespVO> getAction(@RequestParam("id") Long id) {
        DeviceActionDO action = deviceActionService.getAction(id);
        return success(BeanUtils.toBean(action, DeviceActionRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得设备动作列表（可按设备类型过滤）")
    @Parameter(name = "deviceTypeCode", description = "设备类型编码", example = "PLATE_READER")
    @PreAuthorize("@ss.hasPermission('device:action:query')")
    public CommonResult<List<DeviceActionRespVO>> getActionList(@RequestParam(value = "deviceTypeCode", required = false) String deviceTypeCode) {
        List<DeviceActionDO> list = deviceActionService.getActionList(deviceTypeCode);
        return success(BeanUtils.toBean(list, DeviceActionRespVO.class));
    }

}

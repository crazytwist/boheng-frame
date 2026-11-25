package cn.boheng.frame.module.device.controller.admin.command;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandCloseReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;
/**
 * 命令记录接口。只查询，不提供删除
 */

@Tag(name = "管理后台 - 设备命令")
@RestController
@RequestMapping("/device/command")
@Validated
public class DeviceCommandController {
    /**
     * 命令记录服务
     */

    @Resource
    private DeviceCommandService deviceCommandService;

    @GetMapping("/page")
    @Operation(summary = "命令记录分页")
    @PreAuthorize("@ss.hasPermission('device:command:query')")
    public CommonResult<PageResult<DeviceCommandRespVO>> getCommandPage(@Valid DeviceCommandPageReqVO pageReqVO) {
        return success(deviceCommandService.getCommandPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "命令记录详情")
    @PreAuthorize("@ss.hasPermission('device:command:query')")
    public CommonResult<DeviceCommandRespVO> getCommand(@RequestParam("id") Long id) {
        return success(deviceCommandService.getCommand(id));
    }

    @PostMapping("/close")
    @Operation(summary = "人工结束命令",
            description = "回调丢失等情况下由运维给定结论。只接受非终态命令，且必须明确是否可重试")
    @PreAuthorize("@ss.hasPermission('device:command:close')")
    public CommonResult<DeviceCommandRespVO> closeCommand(@Valid @RequestBody DeviceCommandCloseReqVO reqVO) {
        return success(deviceCommandService.manualClose(reqVO));
    }

}

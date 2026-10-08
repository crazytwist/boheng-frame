package cn.boheng.frame.module.device.controller.admin.command;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 设备命令")
@RestController
@RequestMapping("/device/command")
@Validated
public class DeviceCommandController {

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

}

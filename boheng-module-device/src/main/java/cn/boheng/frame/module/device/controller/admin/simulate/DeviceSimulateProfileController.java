package cn.boheng.frame.module.device.controller.admin.simulate;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfilePageReqVO;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfileRespVO;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfileSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;
import cn.boheng.frame.module.device.service.simulate.DeviceSimulateProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 仿真 HTTP 配置的管理接口。请求报文、返回报文、等待时间和轮询间隔都在这里改。
 */
@Tag(name = "管理后台 - 仿真 HTTP")
@RestController
@RequestMapping("/device/simulate/profile")
@Validated
public class DeviceSimulateProfileController {

    /**
     * 仿真配置服务
     */
    @Resource
    private DeviceSimulateProfileService profileService;

    @PostMapping("/create")
    @Operation(summary = "创建仿真 HTTP 配置")
    @PreAuthorize("@ss.hasPermission('device:simulate:create')")
    public CommonResult<Long> createProfile(@Valid @RequestBody DeviceSimulateProfileSaveReqVO createReqVO) {
        return success(profileService.createProfile(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新仿真 HTTP 配置")
    @PreAuthorize("@ss.hasPermission('device:simulate:update')")
    public CommonResult<Boolean> updateProfile(@Valid @RequestBody DeviceSimulateProfileSaveReqVO updateReqVO) {
        profileService.updateProfile(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除仿真 HTTP 配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('device:simulate:delete')")
    public CommonResult<Boolean> deleteProfile(@RequestParam("id") Long id) {
        profileService.deleteProfile(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得仿真 HTTP 配置")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('device:simulate:query')")
    public CommonResult<DeviceSimulateProfileRespVO> getProfile(@RequestParam("id") Long id) {
        DeviceSimulateProfileDO profile = profileService.getProfile(id);
        return success(BeanUtils.toBean(profile, DeviceSimulateProfileRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得仿真 HTTP 配置分页")
    @PreAuthorize("@ss.hasPermission('device:simulate:query')")
    public CommonResult<PageResult<DeviceSimulateProfileRespVO>> getProfilePage(@Valid DeviceSimulateProfilePageReqVO pageReqVO) {
        PageResult<DeviceSimulateProfileDO> page = profileService.getProfilePage(pageReqVO);
        return success(BeanUtils.toBean(page, DeviceSimulateProfileRespVO.class));
    }

}

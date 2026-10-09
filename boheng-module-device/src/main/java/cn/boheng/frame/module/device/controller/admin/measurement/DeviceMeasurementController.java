package cn.boheng.frame.module.device.controller.admin.measurement;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementPageReqVO;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementRespVO;
import cn.boheng.frame.module.device.service.measurement.DeviceMeasurementService;
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
/**
 * 测量记录接口
 */

@Tag(name = "管理后台 - 设备测量")
@RestController
@RequestMapping("/device/measurement")
@Validated
public class DeviceMeasurementController {
    /**
     * 测量记录服务
     */

    @Resource
    private DeviceMeasurementService deviceMeasurementService;

    @GetMapping("/page")
    @Operation(summary = "测量记录分页")
    @PreAuthorize("@ss.hasPermission('device:measurement:query')")
    public CommonResult<PageResult<DeviceMeasurementRespVO>> getMeasurementPage(@Valid DeviceMeasurementPageReqVO pageReqVO) {
        return success(deviceMeasurementService.getMeasurementPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "测量详情，含孔级读数和分析结论")
    @PreAuthorize("@ss.hasPermission('device:measurement:query')")
    public CommonResult<DeviceMeasurementRespVO> getMeasurement(@RequestParam("id") Long id) {
        return success(deviceMeasurementService.getMeasurement(id));
    }

}

package cn.boheng.frame.module.device.controller.admin.codec;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPageReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecPreviewReqVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecRespVO;
import cn.boheng.frame.module.device.controller.admin.codec.vo.DeviceCodecSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import cn.boheng.frame.module.device.service.codec.DeviceCodecService;
import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;
/**
 * 解析规则接口
 */

@Tag(name = "管理后台 - 设备解析规则")
@RestController
@RequestMapping("/device/codec")
@Validated
public class DeviceCodecController {
    /**
     * 解析规则服务
     */

    @Resource
    private DeviceCodecService deviceCodecService;

    @PostMapping("/create")
    @Operation(summary = "创建解析规则")
    @PreAuthorize("@ss.hasPermission('device:codec:create')")
    public CommonResult<Long> createCodec(@Valid @RequestBody DeviceCodecSaveReqVO createReqVO) {
        return success(deviceCodecService.createCodec(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新解析规则")
    @PreAuthorize("@ss.hasPermission('device:codec:update')")
    public CommonResult<Boolean> updateCodec(@Valid @RequestBody DeviceCodecSaveReqVO updateReqVO) {
        deviceCodecService.updateCodec(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除解析规则")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('device:codec:delete')")
    public CommonResult<Boolean> deleteCodec(@RequestParam("id") Long id) {
        deviceCodecService.deleteCodec(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "解析规则分页")
    @PreAuthorize("@ss.hasPermission('device:codec:query')")
    public CommonResult<PageResult<DeviceCodecRespVO>> getCodecPage(@Valid DeviceCodecPageReqVO pageReqVO) {
        PageResult<DeviceCodecDO> page = deviceCodecService.getCodecPage(pageReqVO);
        return success(BeanUtils.toBean(page, DeviceCodecRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "解析规则详情")
    @PreAuthorize("@ss.hasPermission('device:codec:query')")
    public CommonResult<DeviceCodecRespVO> getCodec(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(deviceCodecService.getCodec(id), DeviceCodecRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "启用中的解析规则")
    @PreAuthorize("@ss.hasPermission('device:codec:query')")
    public CommonResult<List<DeviceCodecRespVO>> getCodecList() {
        return success(BeanUtils.toBean(deviceCodecService.getEnabledList(), DeviceCodecRespVO.class));
    }

    @PostMapping("/preview")
    @Operation(summary = "用样例报文试解析")
    @PreAuthorize("@ss.hasPermission('device:codec:query')")
    public CommonResult<JSONObject> preview(@Valid @RequestBody DeviceCodecPreviewReqVO reqVO) {
        return success(deviceCodecService.preview(reqVO));
    }

}

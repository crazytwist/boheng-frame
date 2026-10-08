package cn.boheng.frame.module.device.controller.admin.action.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 设备动作定义 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备动作定义 创建/修改 Request VO")
@Data
public class DeviceActionSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE_READER")
    @NotEmpty(message = "设备类型编码不能为空")
    @Size(max = 64, message = "设备类型编码长度不能超过 64 个字符")
    private String deviceTypeCode;

    @Schema(description = "动作编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "READ_PLATE")
    @NotEmpty(message = "动作编码不能为空")
    @Size(max = 64, message = "动作编码长度不能超过 64 个字符")
    private String actionCode;

    @Schema(description = "动作名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "读板")
    @NotEmpty(message = "动作名称不能为空")
    @Size(max = 128, message = "动作名称长度不能超过 128 个字符")
    private String actionName;

    @Schema(description = "映射 SiLA 标准特性名", example = "com.sila_standard.run_control")
    @Size(max = 128, message = "标准特性名长度不能超过 128 个字符")
    private String standardFeature;

    @Schema(description = "参数模式 JSON（前端按 schema 动态渲染表单）",
            example = "{\"wavelength\":{\"label\":\"主波长\",\"type\":\"INTEGER\",\"unit\":\"nm\",\"required\":true}}")
    private String paramSchema;

    @Schema(description = "厂商映射（脚本名/方法名/协议命令）", example = "Magellan.ReadPlate")
    @Size(max = 255, message = "厂商映射长度不能超过 255 个字符")
    private String vendorRef;

    @Schema(description = "能力来源: DEVICE_DECLARED 驱动上报/MANUAL 手工", example = "MANUAL")
    @Size(max = 32, message = "能力来源长度不能超过 32 个字符")
    private String source;

    @Schema(description = "预估耗时(毫秒)；< 3000 才允许 SYNC", example = "30000")
    private Long estimateDurationMs;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

}

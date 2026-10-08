package cn.boheng.frame.module.device.controller.admin.property.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 设备可观测属性定义 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备可观测属性定义 创建/修改 Request VO")
@Data
public class DevicePropertySaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE_READER")
    @NotEmpty(message = "设备类型编码不能为空")
    @Size(max = 64, message = "设备类型编码长度不能超过 64 个字符")
    private String deviceTypeCode;

    @Schema(description = "属性编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEMPERATURE")
    @NotEmpty(message = "属性编码不能为空")
    @Size(max = 64, message = "属性编码长度不能超过 64 个字符")
    private String propertyCode;

    @Schema(description = "属性名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "温度")
    @NotEmpty(message = "属性名称不能为空")
    @Size(max = 128, message = "属性名称长度不能超过 128 个字符")
    private String propertyName;

    @Schema(description = "数据类型: STRING/INTEGER/DECIMAL/BOOLEAN/ENUM/JSON", example = "DECIMAL")
    @NotEmpty(message = "数据类型不能为空")
    @Size(max = 32, message = "数据类型长度不能超过 32 个字符")
    private String dataType;

    @Schema(description = "单位", example = "℃")
    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Schema(description = "是否可读", example = "true")
    private Boolean readable;

    @Schema(description = "是否可订阅（仪器支持推送时置 true）", example = "false")
    private Boolean subscribable;

    @Schema(description = "轮询间隔(秒)", example = "30")
    private Integer pollIntervalSec;

    @Schema(description = "能力来源: DEVICE_DECLARED 驱动上报/MANUAL 手工", example = "MANUAL")
    @Size(max = 32, message = "能力来源长度不能超过 32 个字符")
    private String source;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

}

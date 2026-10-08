package cn.boheng.frame.module.device.controller.admin.paramset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 参数集预设 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 参数集预设 创建/修改 Request VO")
@Data
public class DeviceParamSetSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "参数集编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PS-ABSORB-450")
    @NotEmpty(message = "参数集编码不能为空")
    @Size(max = 64, message = "参数集编码长度不能超过 64 个字符")
    private String paramSetCode;

    @Schema(description = "参数集名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "吸光度 450nm 标准读板")
    @NotEmpty(message = "参数集名称不能为空")
    @Size(max = 128, message = "参数集名称长度不能超过 128 个字符")
    private String paramSetName;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE_READER")
    @NotEmpty(message = "设备类型编码不能为空")
    @Size(max = 64, message = "设备类型编码长度不能超过 64 个字符")
    private String deviceTypeCode;

    @Schema(description = "关联动作编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "READ_PLATE")
    @NotEmpty(message = "关联动作编码不能为空")
    @Size(max = 64, message = "关联动作编码长度不能超过 64 个字符")
    private String actionCode;

    @Schema(description = "参数取值 JSON（与 device_action.param_schema 对齐）",
            example = "{\"wavelength\":450,\"mode\":\"ABSORBANCE\"}")
    @NotEmpty(message = "参数取值不能为空")
    private String paramsJson;

    @Schema(description = "是否已验证（只有已验证才能被用于发起命令）", example = "false")
    private Boolean validated;

    @Schema(description = "验证人", example = "yinan")
    @Size(max = 64, message = "验证人长度不能超过 64 个字符")
    private String validatedBy;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

}

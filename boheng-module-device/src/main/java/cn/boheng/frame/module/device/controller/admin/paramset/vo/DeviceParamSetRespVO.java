package cn.boheng.frame.module.device.controller.admin.paramset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参数集预设 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 参数集预设 Response VO")
@Data
public class DeviceParamSetRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "参数集编码", example = "PS-ABSORB-450")
    private String paramSetCode;

    @Schema(description = "参数集名称", example = "吸光度 450nm 标准读板")
    private String paramSetName;

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "关联动作编码", example = "READ_PLATE")
    private String actionCode;

    @Schema(description = "参数取值 JSON")
    private String paramsJson;

    @Schema(description = "是否已验证", example = "true")
    private Boolean validated;

    @Schema(description = "验证人", example = "yinan")
    private String validatedBy;

    @Schema(description = "验证时间")
    private LocalDateTime validatedTime;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

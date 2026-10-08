package cn.boheng.frame.module.device.controller.admin.action.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备动作定义 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备动作定义 Response VO")
@Data
public class DeviceActionRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "动作编码", example = "READ_PLATE")
    private String actionCode;

    @Schema(description = "动作名称", example = "读板")
    private String actionName;

    @Schema(description = "映射 SiLA 标准特性名", example = "com.sila_standard.run_control")
    private String standardFeature;

    @Schema(description = "参数模式 JSON")
    private String paramSchema;

    @Schema(description = "厂商映射", example = "Magellan.ReadPlate")
    private String vendorRef;

    @Schema(description = "能力来源", example = "MANUAL")
    private String source;

    @Schema(description = "预估耗时(毫秒)", example = "30000")
    private Long estimateDurationMs;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

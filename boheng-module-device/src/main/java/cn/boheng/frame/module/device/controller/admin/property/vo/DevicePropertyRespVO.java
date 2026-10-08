package cn.boheng.frame.module.device.controller.admin.property.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备可观测属性定义 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备可观测属性定义 Response VO")
@Data
public class DevicePropertyRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "属性编码", example = "TEMPERATURE")
    private String propertyCode;

    @Schema(description = "属性名称", example = "温度")
    private String propertyName;

    @Schema(description = "数据类型", example = "DECIMAL")
    private String dataType;

    @Schema(description = "单位", example = "℃")
    private String unit;

    @Schema(description = "是否可读", example = "true")
    private Boolean readable;

    @Schema(description = "是否可订阅", example = "false")
    private Boolean subscribable;

    @Schema(description = "轮询间隔(秒)", example = "30")
    private Integer pollIntervalSec;

    @Schema(description = "能力来源", example = "MANUAL")
    private String source;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

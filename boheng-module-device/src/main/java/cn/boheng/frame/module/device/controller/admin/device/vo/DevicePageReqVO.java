package cn.boheng.frame.module.device.controller.admin.device.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 设备台账 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备台账 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DevicePageReqVO extends PageParam {

    @Schema(description = "设备编码", example = "DEV-001")
    private String deviceCode;

    @Schema(description = "设备名称", example = "读板机")
    private String deviceName;

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "厂商", example = "Tecan")
    private String vendor;

    @Schema(description = "驱动类型", example = "SIMULATED")
    private String driverType;

    @Schema(description = "在线状态: ONLINE/OFFLINE/MAINTENANCE/FAULT", example = "ONLINE")
    private String status;

    @Schema(description = "接入方式: HTTP/MQTT/NODE_RED/SIMULATED", example = "SIMULATED")
    private String connectionType;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}

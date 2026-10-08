package cn.boheng.frame.module.device.controller.admin.action.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 设备动作定义 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备动作定义 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceActionPageReqVO extends PageParam {

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "动作编码", example = "READ_PLATE")
    private String actionCode;

    @Schema(description = "动作名称", example = "读板")
    private String actionName;

    @Schema(description = "SiLA 标准特性名", example = "com.sila_standard.run_control")
    private String standardFeature;

    @Schema(description = "能力来源: DEVICE_DECLARED/MANUAL", example = "MANUAL")
    private String source;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}

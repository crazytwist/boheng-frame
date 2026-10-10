package cn.boheng.frame.module.device.controller.admin.simulate.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 仿真 HTTP 配置的分页条件
 */
@Schema(description = "管理后台 - 仿真 HTTP 配置分页")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceSimulateProfilePageReqVO extends PageParam {

    @Schema(description = "设备编号")
    private Long deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "回收方式: SYNC / CALLBACK / POLL")
    private String returnMode;

    @Schema(description = "启用状态: 0 启用 / 1 停用")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}

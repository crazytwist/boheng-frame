package cn.boheng.frame.module.device.controller.admin.paramset.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 参数集预设 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 参数集预设 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceParamSetPageReqVO extends PageParam {

    @Schema(description = "参数集编码", example = "PS-ABSORB-450")
    private String paramSetCode;

    @Schema(description = "参数集名称", example = "吸光度 450nm")
    private String paramSetName;

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "关联动作编码", example = "READ_PLATE")
    private String actionCode;

    @Schema(description = "是否已验证", example = "true")
    private Boolean validated;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}

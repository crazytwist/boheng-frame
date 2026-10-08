package cn.boheng.frame.module.device.controller.admin.property.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 设备可观测属性定义 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备可观测属性定义 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DevicePropertyPageReqVO extends PageParam {

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "属性编码", example = "TEMPERATURE")
    private String propertyCode;

    @Schema(description = "属性名称", example = "温度")
    private String propertyName;

    @Schema(description = "数据类型: STRING/INTEGER/DECIMAL/BOOLEAN/ENUM/JSON", example = "DECIMAL")
    private String dataType;

    @Schema(description = "能力来源: DEVICE_DECLARED/MANUAL", example = "MANUAL")
    private String source;

    @Schema(description = "启用状态: 0 启用/1 停用", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}

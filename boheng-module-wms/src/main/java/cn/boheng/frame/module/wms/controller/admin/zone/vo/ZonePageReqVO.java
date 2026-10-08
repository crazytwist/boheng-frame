package cn.boheng.frame.module.wms.controller.admin.zone.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 区域树 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 区域树 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ZonePageReqVO extends PageParam {

    @Schema(description = "区域编码", example = "ZONE_LAB_01")
    private String zoneCode;

    @Schema(description = "父区域编码（为空表示根节点）", example = "ZONE_LAB")
    private String parentZoneCode;

    @Schema(description = "区域名称", example = "一楼实验室")
    private String zoneName;

    @Schema(description = "区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义", example = "RACK")
    private String zoneType;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}

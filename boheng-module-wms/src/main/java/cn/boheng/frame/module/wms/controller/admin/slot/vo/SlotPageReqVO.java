package cn.boheng.frame.module.wms.controller.admin.slot.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 槽位 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 槽位 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SlotPageReqVO extends PageParam {

    @Schema(description = "槽位编码", example = "SLOT_RACK_A01_R1C1")
    private String slotCode;

    @Schema(description = "挂载区域编码", example = "RACK_A01")
    private String zoneCode;

    @Schema(description = "槽位类型：STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃", example = "STORAGE")
    private String slotType;

    @Schema(description = "可用状态", example = "0")
    private Integer status;

    @Schema(description = "使用状态：FREE 空闲/OCCUPIED 占用/LOCKED 锁定/CHECKING 盘点中", example = "FREE")
    private String slotStatus;

    @Schema(description = "所属设备编码", example = "TECAN_F200_01")
    private String deviceCode;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}

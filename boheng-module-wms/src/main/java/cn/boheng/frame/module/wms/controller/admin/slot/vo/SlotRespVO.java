package cn.boheng.frame.module.wms.controller.admin.slot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 槽位 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 槽位 Response VO")
@Data
public class SlotRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

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

    @Schema(description = "容量（标准位）", example = "1")
    private Integer capacity;

    @Schema(description = "是否一位一批", example = "false")
    private Boolean uniqueBatch;

    @Schema(description = "扩展数据（JSON，坐标描述 row/col/layer）", example = "JSON 对象，如 rowNo/colNo/layerNo")
    private String extData;

    @Schema(description = "所属设备编码", example = "TECAN_F200_01")
    private String deviceCode;

    @Schema(description = "设备侧位置标识", example = "STACK-3")
    private String devicePositionNo;

    @Schema(description = "已占标准位数（=0 即 FREE）", example = "0")
    private Integer occupiedQty;

    @Schema(description = "转为占用态时间", example = "2026-09-25 10:00:00")
    private LocalDateTime occupiedTime;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;

    @Schema(description = "备注", example = "样本位")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

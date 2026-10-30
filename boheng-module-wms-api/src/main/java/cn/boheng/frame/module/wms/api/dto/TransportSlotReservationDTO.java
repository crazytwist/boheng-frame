package cn.boheng.frame.module.wms.api.dto;

import lombok.Builder;
import lombok.Data;

/** WMS 返回给 Transport 的槽位快照。 */
@Data
@Builder
public class TransportSlotReservationDTO {
    /** 槽位主键。 */
    private Long slotId;
    /** 槽位编码。 */
    private String slotCode;
    /** 区域编码。 */
    private String zoneCode;
    /** 槽位类型。 */
    private String slotType;
    /** 槽位状态。 */
    private String slotStatus;
    /** 槽位设备侧位置标识。 */
    private String devicePositionNo;
    /** 当前可用数量快照。 */
    private Integer availableCount;
    /** 预约标识。 */
    private String reservationKey;
}

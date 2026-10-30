package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

/** Transport 查询 WMS 中转槽位的请求。 */
@Data
public class TransportSlotQueryReqDTO {
    /** 区域编码。 */
    private String zoneCode;
    /** 需要的槽位数量。 */
    private Integer limit = 1;
    /** 槽位类型，默认 BUFFER。 */
    private String slotType = "BUFFER";
    /** 物料或容器编码，可用于适配槽位。 */
    private String materialCode;
}

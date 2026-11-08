package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 槽位契约快照（不暴露 SlotDO）。 */
@Data
public class MaterialSlotDTO implements Serializable {

    private Long id;
    private String slotCode;
    private String zoneCode;
    private String deviceCode;
    private String slotType;
    private String slotStatus;
    private Integer status;
    private Integer capacity;
    private Integer occupiedQty;
}

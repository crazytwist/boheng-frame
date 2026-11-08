package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 面向 Flow 的槽位查询（只读，不锁槽）。 */
@Data
public class MaterialSlotQueryReqDTO implements Serializable {

    private String zoneCode;
    private String deviceCode;
    private String slotType;
    /** 使用状态；默认不限。常用 FREE */
    private String slotStatus;
    /** 启用状态：0 启用；不传不限 */
    private Integer status;
    /** 有界上限，默认 50，最大 200 */
    private Integer limit;
}

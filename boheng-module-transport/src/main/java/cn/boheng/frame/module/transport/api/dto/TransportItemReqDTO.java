package cn.boheng.frame.module.transport.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单个运输对象的起点、终点和业务引用。
 */
@Data
public class TransportItemReqDTO {
    /** 物料或载具引用。 */
    @NotBlank private String loadUnitRef;
    /** 起始槽位引用。 */
    @NotBlank private String sourceSlotRef;
    /** 目标槽位引用。 */
    @NotBlank private String targetSlotRef;
}

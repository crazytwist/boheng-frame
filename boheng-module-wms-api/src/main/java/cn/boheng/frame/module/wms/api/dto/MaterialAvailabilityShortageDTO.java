package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 综合可用性缺项明细。 */
@Data
public class MaterialAvailabilityShortageDTO implements Serializable {

    private String kind;
    private String code;
    private Integer required;
    private Integer available;
    private BigDecimal requiredVolUl;
    private BigDecimal availableVolUl;
    /** true=该项满足 */
    private Boolean ok;
    private String reason;
}

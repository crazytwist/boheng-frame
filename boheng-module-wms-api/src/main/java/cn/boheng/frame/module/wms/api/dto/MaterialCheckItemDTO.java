package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料可用性检查单项。
 */
@Data
public class MaterialCheckItemDTO implements Serializable {

    /** 实例编号 */
    private Long instanceId;

    /** 期望至少剩余数量（可选） */
    private Integer minQty;

    /** 期望至少剩余体积 μL（可选） */
    private BigDecimal minVolUl;

}

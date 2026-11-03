package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 精确消耗明细（契约层）。
 */
@Data
public class MaterialConsumeItemDTO implements Serializable {

    /** 被消耗的实例编号 */
    private Long instanceId;

    /** 离散消耗数量；与 volUl 二选一，优先 volUl；都不传=整件消耗 */
    private Integer qty;

    /** 连续消耗体积（μL） */
    private BigDecimal volUl;

    /** 消耗后状态；可空=按剩余量推导 */
    private String afterStatus;

    /** 明细级备注 */
    private String remark;

}

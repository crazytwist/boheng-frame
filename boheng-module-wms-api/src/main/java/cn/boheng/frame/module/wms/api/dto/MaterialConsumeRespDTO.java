package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 消耗结果（精确消耗与按容器消耗统一）。
 */
@Data
public class MaterialConsumeRespDTO implements Serializable {

    /** 产生的流水编号（顺序与明细一致） */
    private List<Long> movementIds;

    /** 实际被消耗的实例编号（顺序与明细一致） */
    private List<Long> consumedInstanceIds;

    /** 本批操作批次号 */
    private String operationId;

    /** true=同 idempotentKey 回放首次结果，库存/流水未再变更 */
    private Boolean idempotentReplay;

}

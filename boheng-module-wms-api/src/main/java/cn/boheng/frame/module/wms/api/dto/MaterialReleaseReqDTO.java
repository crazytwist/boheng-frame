package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * release：有归属须校验所有者；释放后清三列归属。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialReleaseReqDTO extends MaterialOperationTraceDTO {

    /** 实例编号 */
    private Long instanceId;

    /** 预留号（有归属实例时必填） */
    private String reservationId;

    /** 执行号（有归属实例时必填） */
    private String executionId;

}

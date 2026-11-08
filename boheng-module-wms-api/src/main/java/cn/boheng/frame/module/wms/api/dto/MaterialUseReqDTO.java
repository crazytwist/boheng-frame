package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * use：无归属 AVAILABLE→IN_USE；有归属 RESERVED 须匹配 reservationId/executionId。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialUseReqDTO extends MaterialOperationTraceDTO {

    /** 实例编号 */
    private Long instanceId;

    /** 预留号（有归属实例时必填） */
    private String reservationId;

    /** 执行号（有归属实例时必填） */
    private String executionId;

}

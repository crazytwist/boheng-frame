package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 最小 release：IN_USE/RESERVED → AVAILABLE（D2-a）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialReleaseReqDTO extends MaterialOperationTraceDTO {

    /** 实例编号 */
    private Long instanceId;

}

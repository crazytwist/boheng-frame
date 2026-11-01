package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 最小 use：AVAILABLE/RESERVED → IN_USE（D2-a，无完整预留）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialUseReqDTO extends MaterialOperationTraceDTO {

    /** 实例编号 */
    private Long instanceId;

}

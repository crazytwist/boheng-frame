package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * 精确消耗请求（契约层）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialConsumeReqDTO extends MaterialOperationTraceDTO {

    /** 消耗明细；不可空 */
    private List<MaterialConsumeItemDTO> items;

}

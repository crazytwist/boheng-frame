package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 物料可用性检查单项结果。
 */
@Data
public class MaterialCheckItemResultDTO implements Serializable {

    private Long instanceId;
    private String instanceCode;
    private String instanceStatus;
    /** 该项是否可用 */
    private Boolean available;
    /** 不可用原因；可用时为空 */
    private String reason;

}

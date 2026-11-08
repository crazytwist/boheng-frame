package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 未能自动释放的实例（通常为 IN_USE）。 */
@Data
public class MaterialPendingReleaseDTO implements Serializable {

    private Long instanceId;
    private String instanceCode;
    private String instanceStatus;
    private String reason;
}

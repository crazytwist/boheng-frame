package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * use / release 统一结果。
 */
@Data
public class MaterialUseReleaseRespDTO implements Serializable {

    private Long movementId;
    private Long instanceId;
    private String beforeStatus;
    private String afterStatus;
    private String operationId;
    private Boolean idempotentReplay;

}

package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 按执行释放结果：只自动释放 RESERVED；IN_USE 返回待处置。 */
@Data
public class MaterialReleaseByExecutionRespDTO implements Serializable {

    private List<Long> releasedInstanceIds;
    private List<Long> movementIds;
    private List<MaterialPendingReleaseDTO> pendingInUse;
    private String operationId;
    private Boolean idempotentReplay;
}

package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 按执行释放预留结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceReleaseByExecutionRespVO {

    private List<Long> releasedInstanceIds;
    private List<Long> movementIds;
    private List<Pending> pendingInUse;
    private String operationId;
    private Boolean idempotentReplay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pending {
        private Long instanceId;
        private String instanceCode;
        private String instanceStatus;
        private String reason;
    }
}

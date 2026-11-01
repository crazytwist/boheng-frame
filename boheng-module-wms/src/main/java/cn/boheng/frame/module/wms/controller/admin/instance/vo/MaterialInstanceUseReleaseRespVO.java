package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 物料实例 use/release 结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceUseReleaseRespVO {

    @Schema(description = "流水编号")
    private Long movementId;

    @Schema(description = "实例编号")
    private Long instanceId;

    @Schema(description = "变更前状态")
    private String beforeStatus;

    @Schema(description = "变更后状态")
    private String afterStatus;

    @Schema(description = "操作批次号")
    private String operationId;

    @Schema(description = "是否幂等回放")
    private Boolean idempotentReplay;

}

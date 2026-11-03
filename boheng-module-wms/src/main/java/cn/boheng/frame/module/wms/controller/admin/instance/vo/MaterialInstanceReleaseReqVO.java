package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 物料实例 最小释放 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaterialInstanceReleaseReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "实例编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "实例编号不能为空")
    private Long instanceId;

}

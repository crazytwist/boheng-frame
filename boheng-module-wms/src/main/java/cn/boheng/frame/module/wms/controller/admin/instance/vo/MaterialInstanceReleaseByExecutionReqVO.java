package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 按执行释放预留 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceReleaseByExecutionReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "执行编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "执行编号不能为空")
    private String executionId;

    @Schema(description = "释放原因")
    private String reason;
}

package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 物料实例预留 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceReserveReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "执行编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "执行编号不能为空")
    private String executionId;

    @Schema(description = "实例编号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "预留实例列表不能为空")
    private List<Long> instanceIds;

    @Schema(description = "到期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "预留到期时间不能为空")
    private LocalDateTime reservedUntil;
}

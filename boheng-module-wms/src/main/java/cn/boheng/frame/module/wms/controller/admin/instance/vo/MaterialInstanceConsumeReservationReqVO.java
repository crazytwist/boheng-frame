package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Schema(description = "管理后台 - 按预留消耗 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceConsumeReservationReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "预留号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "预留号不能为空")
    private String reservationId;

    @Schema(description = "执行编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "执行编号不能为空")
    private String executionId;

    @Schema(description = "消耗明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "消耗明细不能为空")
    @Valid
    private List<MaterialInstanceConsumeReqVO.Item> items;
}

package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 根据液体类型和物料类型查询物料信息（请求体与 qinghua LabwareLiquidTypeQuery 对齐）
 */
@Data
@Schema(description = "根据液体类型和物料类型查询物料信息")
public class MaterialLiquidTypeQueryReqVO {

    @NotBlank(message = "位置区域不能为空")
    @Schema(description = "区域编码（对应 wms_slot_info.zone_code，无父级区域树）", name = "PositionArea")
    @JsonProperty("PositionArea")
    private String positionArea;

    @NotNull(message = "液体类型不能为空")
    @Schema(description = "液体类型", name = "LiquidType")
    @JsonProperty("LiquidType")
    private Integer liquidType;

    @NotNull(message = "物料类型不能为空")
    @Schema(description = "物料类型", name = "LabwareType")
    @JsonProperty("LabwareType")
    private Integer labwareType;

    @Schema(description = "批次号/工单号", name = "BatchId")
    @JsonProperty("BatchId")
    private String batchId;
}

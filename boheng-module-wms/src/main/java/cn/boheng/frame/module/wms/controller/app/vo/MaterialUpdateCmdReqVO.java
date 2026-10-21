package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "设置物料当前状态（位置、液量、状态等）")
public class MaterialUpdateCmdReqVO {

    @NotNull(message = "物料ID不能为空")
    @Schema(description = "物料实例主键", name = "LabwareId")
    @JsonProperty("LabwareId")
    private Long labwareId;

    @Schema(description = "物料状态", name = "Status")
    @JsonProperty("Status")
    private Integer status;

    @Schema(description = "物料位置（区域/槽位编码，或 区域|载具孔位）", name = "Position")
    @JsonProperty("Position")
    private String position;

    @Schema(description = "液体类型", name = "Liquid")
    @JsonProperty("Liquid")
    private Integer liquidType;

    @Schema(description = "数量", name = "Quantity")
    @JsonProperty("Quantity")
    private Integer quantity;

    @Schema(description = "工单/批次 ID", name = "BatchId")
    @JsonProperty("BatchId")
    private Integer batchId;

    @Schema(description = "物料类型", name = "Type")
    @JsonProperty("Type")
    private Integer labwareType;

    @Schema(description = "液量", name = "Volume")
    @JsonProperty("Volume")
    private Double volume;
}

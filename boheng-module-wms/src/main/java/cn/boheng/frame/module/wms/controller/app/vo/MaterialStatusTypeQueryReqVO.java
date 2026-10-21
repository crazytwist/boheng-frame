package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "根据状态和类型查询物料信息")
public class MaterialStatusTypeQueryReqVO {

    @NotBlank(message = "位置区域不能为空")
    @Schema(description = "区域编码（对应 wms_slot_info.zone_code，无父级区域树）", name = "PositionArea")
    @JsonProperty("PositionArea")
    private String positionArea;

    @NotNull(message = "物料状态不能为空")
    @Schema(description = "物料状态", name = "LabwareStatus")
    @JsonProperty("LabwareStatus")
    private Integer labwareStatus;

    @NotNull(message = "物料类型不能为空")
    @Schema(description = "物料类型", name = "LabwareType")
    @JsonProperty("LabwareType")
    private Integer labwareType;
}

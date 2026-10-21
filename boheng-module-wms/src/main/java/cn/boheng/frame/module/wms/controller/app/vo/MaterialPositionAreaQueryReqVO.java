package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "按区域查询可用位置")
public class MaterialPositionAreaQueryReqVO {

    @NotBlank(message = "区域编号不能为空")
    @Schema(description = "区域编码（wms_slot_info.zone_code）或 载具位置|（载具内孔位查询）", name = "PositionArea")
    @JsonProperty("PositionArea")
    private String positionArea;

    @NotNull(message = "器具类型不能为空")
    @Schema(description = "器具类型（对应槽位 suitable_materials 中 CONTAINER.materialCode）", name = "LabwareType")
    @JsonProperty("LabwareType")
    private Integer labwareType;

    @NotNull(message = "数量不能为空")
    @Schema(description = "需要的空闲位置数量", name = "Count")
    @JsonProperty("Count")
    private Integer count;
}

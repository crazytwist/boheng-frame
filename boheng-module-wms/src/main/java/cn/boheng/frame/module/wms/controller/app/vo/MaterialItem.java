package cn.boheng.frame.module.wms.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "实验室物品")
public class MaterialItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "耗材ID（物料实例主键）")
    private Long id;

    @Schema(description = "所属工单")
    private Integer batchId;

    @Schema(description = "物料类别  50MLCentrifugeTube = 1, TBottle = 2 ")
    private Integer type;

    @Schema(description = "物料编码")
    private String code;

    @Schema(description = "物料位置 具体到 .A1")
    private String position;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "最大容量")
    private Double capacity;

    @Schema(description = "当前液量")
    private Double volume;

    @Schema(description = "当前液类型")
    private Integer liquid;

    @Schema(description = "实验室物品状态", example = "New,Using,Discard,Harvest")
    private Integer status;

    @Schema(description = "子项目")
    private List<MaterialItem> childLabware;
}

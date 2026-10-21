package cn.boheng.frame.module.wms.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "枪头盒资源")
public class TipResourceRespVO {

    @Schema(description = "物料实例主键")
    private String id;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "当前剩余数量")
    private Integer capacity;

    @Schema(description = "最大容量")
    private Integer rangeMax;
}

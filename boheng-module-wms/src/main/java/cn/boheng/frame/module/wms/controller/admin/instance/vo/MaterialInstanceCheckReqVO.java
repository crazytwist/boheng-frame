package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 物料实例 可用性检查 Request VO")
@Data
public class MaterialInstanceCheckReqVO {

    @Schema(description = "检查明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "检查明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @Schema(description = "实例编号", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long instanceId;
        @Schema(description = "期望至少剩余数量")
        private Integer minQty;
        @Schema(description = "期望至少剩余体积 μL")
        private BigDecimal minVolUl;
    }

}

package cn.boheng.frame.module.wms.controller.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Tip 头消耗")
public class TipConsumeReqVO {

    @NotNull(message = "id不能为空")
    @Schema(description = "Tip 盒物料实例主键")
    private Long id;

    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于 0")
    @Schema(description = "消耗个数")
    private Integer count;
}

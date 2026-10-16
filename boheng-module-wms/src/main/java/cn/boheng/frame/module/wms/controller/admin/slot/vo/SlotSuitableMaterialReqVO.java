package cn.boheng.frame.module.wms.controller.admin.slot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 槽位 适用物料 更新 Request VO
 *
 * <p>只更新槽位的适用物料字段，不影响其他字段（避免前端回填全部必填字段）。
 * 支持批量：ids 传多个槽位时，对每个槽位应用同一份适用物料。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 槽位 适用物料更新 Request VO")
@Data
public class SlotSuitableMaterialReqVO {

    @Schema(description = "槽位编号列表（支持批量）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "槽位编号列表不能为空")
    private List<Long> ids;

    @Schema(description = "适用物料（JSON 数组，可绑定试剂/容器）", example = "[{\"kind\":\"CONTAINER\",\"materialId\":1,\"materialCode\":\"TUBE_1.5\",\"materialName\":\"1.5mL离心管\"}]")
    private String suitableMaterials;

}

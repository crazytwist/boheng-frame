package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 物料实例 上架 Request VO
 *
 * <p>把「未落位 / 在别的槽位」的顶层实例放到目标槽位。
 * 数据层面 = {@code root_slot_id} 由 NULL 或旧值改为目标槽位。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 上架 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstancePutInReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "实例编号（仅顶层实例可上架）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "实例编号不能为空")
    private Long instanceId;

    @Schema(description = "目标槽位编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "目标槽位编号不能为空")
    private Long slotId;

}

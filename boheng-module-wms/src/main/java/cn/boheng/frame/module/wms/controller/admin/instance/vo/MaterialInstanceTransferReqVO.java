package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 物料实例 转移 Request VO
 *
 * <p>把在架实例从当前槽位挪到另一个槽位。数据层面 = {@code root_slot_id} 由旧值改为新值。
 * 记一条 MOVE 流水（from / to 同时有值），不拆成「下架 + 上架」两条。</p>
 *
 * <p>整盒（整树）转移只需传顶层实例编号，子实例跟随移动，只记顶层一条流水。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 转移 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceTransferReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "实例编号（仅顶层实例可转移）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "实例编号不能为空")
    private Long instanceId;

    @Schema(description = "目标槽位编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "目标槽位编号不能为空")
    private Long targetSlotId;

}

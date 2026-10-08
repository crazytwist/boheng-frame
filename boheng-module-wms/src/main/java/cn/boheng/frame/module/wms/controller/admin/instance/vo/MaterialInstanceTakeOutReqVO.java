package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 物料实例 下架 Request VO
 *
 * <p>把在架实例从槽位上取下。数据层面 = {@code root_slot_id} 置空。</p>
 *
 * <p>⚠️ 下架**不**引入新的实例状态：「在不在架上」是位置（root_slot_id），
 * 「能不能用」是状态（instance_status），两者正交。如需同步改状态
 * （临时取出去用 → IN_USE，报废 → DISCARDED），用 {@link #afterStatus} 指定。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 下架 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceTakeOutReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "实例编号（仅顶层实例可下架）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "实例编号不能为空")
    private Long instanceId;

    @Schema(description = "下架后的实例状态（可空 = 不改状态）：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃", example = "IN_USE")
    private String afterStatus;

}

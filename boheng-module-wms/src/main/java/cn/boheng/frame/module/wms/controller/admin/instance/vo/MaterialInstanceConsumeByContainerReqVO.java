package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 物料实例 按容器消耗 Request VO
 *
 * <p>调用方只知道「要从这个容器拿 N 个」，不知道具体是哪几个子实例时的用法。
 * 后端从该容器的**可用子实例**里按策略挑选 N 个，逐个消耗（每个子实例一条流水，
 * 共享同一个 operationId）。</p>
 *
 * <p>典型场景：24 孔离心管架用掉 4 根 → rootInstanceId = 管架实例，count = 4。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 按容器消耗 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceConsumeByContainerReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "容器实例编号（被消耗子实例的父实例）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "容器实例编号不能为空")
    private Long rootInstanceId;

    @Schema(description = "领取数量（从可用子实例中挑选的个数）", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "领取数量不能为空")
    @Positive(message = "领取数量必须大于 0")
    private Integer count;

    @Schema(description = "挑选策略：POSITION 按位置顺序（默认，A1→A2→…）/ ID 按创建先后", example = "POSITION")
    private String strategy;

    @Schema(description = "消耗后的子实例状态（可空 = 默认 USED 已用完）", example = "USED")
    private String afterStatus;

    @Schema(description = "是否允许挑走处于 RESERVED 已预留的子实例（默认 false，只挑 AVAILABLE）", example = "false")
    private Boolean includeReserved;

}

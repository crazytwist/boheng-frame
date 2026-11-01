package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 物料实例 消耗结果 VO（精确消耗与按容器消耗统一返回）。
 *
 * <p>认领 wms TODO B1 / doc/06 W-1.6：原先 {@code consume} 返回流水 id、
 * {@code consume-by-container} 返回实例 id，签名相同语义分裂，统一为本 VO。</p>
 */
@Schema(description = "管理后台 - 物料实例 消耗结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceConsumeRespVO {

    @Schema(description = "产生的流水编号列表（顺序与明细一致）")
    private List<Long> movementIds;

    @Schema(description = "实际被消耗的实例编号列表（顺序与明细一致）")
    private List<Long> consumedInstanceIds;

    @Schema(description = "本批操作批次号")
    private String operationId;

    @Schema(description = "是否幂等回放：true=同 idempotentKey 返回首次结果，库存/流水未再变更")
    private Boolean idempotentReplay;

}

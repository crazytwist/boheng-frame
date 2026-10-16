package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料实例 批量铺货上架 Request VO
 *
 * <p>选中一批空闲槽位，按同一种物料（试剂 / 容器）批量生成实例并落位。
 * 每个槽位各生成 1 个顶层实例，实例编码由后端自动生成，复用创建逻辑以正确记录建账流水与槽位占用。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 批量铺货上架 Request VO")
@Data
public class MaterialInstanceBatchPutInReqVO {

    @Schema(description = "目标槽位编号列表（每个槽位各生成 1 个实例）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "目标槽位列表不能为空")
    private List<Long> slotIds;

    @Schema(description = "物料类型：CONTENT 试剂/内容物，CONTAINER 容器", requiredMode = Schema.RequiredMode.REQUIRED, example = "CONTAINER")
    @NotEmpty(message = "物料类型不能为空")
    private String materialKind;

    @Schema(description = "物料编号（CONTENT 对应 content_def.id，CONTAINER 对应 container_type.id）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "物料编号不能为空")
    private Long materialId;

    @Schema(description = "容器类型编号（materialKind=CONTENT 时必填，用于装载该试剂）", example = "2048")
    private Long containerTypeId;

    @Schema(description = "容器类型编码（冗余，可不传，后端回填）", example = "TUBE_1.5")
    @Size(max = 64, message = "容器类型编码长度不能超过 64 个字符")
    private String containerTypeCode;

    @Schema(description = "实例状态", example = "AVAILABLE")
    @Size(max = 32, message = "实例状态长度不能超过 32 个字符")
    private String instanceStatus;

    @Schema(description = "当前体积（μL）", example = "200.00")
    private BigDecimal currentVolUl;

    @Schema(description = "当前数量（个）", example = "1")
    private Integer currentCount;

    @Schema(description = "条码", example = "6901234567890")
    @Size(max = 128, message = "条码长度不能超过 128 个字符")
    private String barcode;

    @Schema(description = "备注", example = "批量铺货")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;

}

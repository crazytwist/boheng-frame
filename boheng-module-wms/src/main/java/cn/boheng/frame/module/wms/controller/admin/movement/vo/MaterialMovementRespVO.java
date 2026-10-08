package cn.boheng.frame.module.wms.controller.admin.movement.vo;

import cn.boheng.frame.module.wms.enums.MovementTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料流水 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料流水 Response VO")
@Data
public class MaterialMovementRespVO {

    @Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "流水类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "PUT_IN")
    private String movementType;

    @Schema(description = "流水类型名称", example = "上架")
    private String movementTypeName;

    @Schema(description = "业务来源", example = "MANUAL")
    private String bizSource;

    @Schema(description = "操作批次号", example = "OP-20260926-0001")
    private String operationId;

    // ========== 主体 ==========

    @Schema(description = "实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long instanceId;

    @Schema(description = "实例编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "INST-580689")
    private String instanceCode;

    @Schema(description = "根实例编号", example = "1024")
    private Long rootInstanceId;

    @Schema(description = "容器类型编码", example = "PLATE_96_WELL")
    private String containerTypeCode;

    @Schema(description = "内容物编码", example = "PH-BUFFER-7")
    private String contentDefCode;

    @Schema(description = "内容物类型", example = "BUFFER")
    private String contentType;

    // ========== 位置 ==========

    @Schema(description = "源槽位编号", example = "2048")
    private Long fromSlotId;

    @Schema(description = "源槽位编码", example = "Zone_Rack_Reagent_R1C1")
    private String fromSlotCode;

    @Schema(description = "源区域编码", example = "Zone_Rack_Reagent")
    private String fromZoneCode;

    @Schema(description = "目标槽位编号", example = "2048")
    private Long toSlotId;

    @Schema(description = "目标槽位编码", example = "Zone_Rack_Reagent_R2C3")
    private String toSlotCode;

    @Schema(description = "目标区域编码", example = "Zone_Rack_Reagent")
    private String toZoneCode;

    // ========== 状态 ==========

    @Schema(description = "变更前实例状态", example = "AVAILABLE")
    private String beforeStatus;

    @Schema(description = "变更后实例状态", example = "USED")
    private String afterStatus;

    // ========== 数量 / 体积 ==========

    @Schema(description = "变更前数量（个）", example = "24")
    private Integer beforeQty;

    @Schema(description = "变更量（个，负为消耗）", example = "-4")
    private Integer changeQty;

    @Schema(description = "变更后数量（个）", example = "20")
    private Integer afterQty;

    @Schema(description = "变更前体积（μL）", example = "500.00")
    private BigDecimal beforeVolUl;

    @Schema(description = "变更量（μL，负为消耗）", example = "-200.00")
    private BigDecimal changeVolUl;

    @Schema(description = "变更后体积（μL）", example = "300.00")
    private BigDecimal afterVolUl;

    // ========== 追溯 ==========

    @Schema(description = "关联业务类型", example = "TASK")
    private String refType;

    @Schema(description = "关联业务编号", example = "TASK-001")
    private String refId;

    @Schema(description = "关联业务单号", example = "TK202609260001")
    private String refNo;

    @Schema(description = "幂等键", example = "task-001-consume-1")
    private String idempotentKey;

    // ========== 操作者 ==========

    @Schema(description = "操作人", example = "1")
    private String operator;

    @Schema(description = "操作者类型", example = "USER")
    private String operatorType;

    @Schema(description = "业务操作时间")
    private LocalDateTime operateTime;

    @Schema(description = "备注", example = "实验消耗")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 冗余展示名：由 movementType 推导，避免前端硬编码映射
     */
    public String getMovementTypeName() {
        return MovementTypeEnum.getNameByType(movementType);
    }

}

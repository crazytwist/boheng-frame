package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/**
 * 物料实例 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 Response VO")
@Data
public class MaterialInstanceRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "实例编码", example = "PLATE96-000123")
    private String instanceCode;

    @Schema(description = "实例名称", example = "第 1 块 96 孔板")
    private String instanceName;

    @Schema(description = "条码", example = "6901234567890")
    private String barcode;

    @Schema(description = "容器类型编号", example = "1024")
    private Long containerTypeId;

    @Schema(description = "容器类型编码（冗余）", example = "PLATE_96_WELL")
    private String containerTypeCode;

    @Schema(description = "父实例编号（为空表示顶层）", example = "512")
    private Long parentInstanceId;

    @Schema(description = "在父容器中的位置", example = "A1")
    private String parentPositionCode;

    @Schema(description = "物化路径", example = "/512/1024")
    private String instancePath;

    @Schema(description = "根实例编号", example = "512")
    private Long rootInstanceId;

    @Schema(description = "落位槽位编号（仅顶层实例）", example = "2048")
    private Long rootSlotId;

    @Schema(description = "落位槽位编码（冗余）", example = "SLOT_RACK_A01_R1C1")
    private String rootSlotCode;

    @Schema(description = "内容物定义编号（CARRIER 与空容器为空）", example = "2048")
    private Long contentDefId;

    @Schema(description = "内容物编码（冗余）", example = "PH-BUFFER-7")
    private String contentDefCode;

    @Schema(description = "内容物类型快照（CARRIER 为空；空容器为 EMPTY）", example = "BUFFER")
    private String contentType;

    @Schema(description = "当前体积（μL）", example = "200.00")
    private BigDecimal currentVolUl;

    @Schema(description = "当前数量（个）", example = "1")
    private Integer currentCount;

    @Schema(description = "扩展数据（JSON，实例级覆盖描述）", example = "JSON 对象，如 concentration")
    private String extData;

    @Schema(description = "实例状态：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃", example = "AVAILABLE")
    private String instanceStatus;

    @Schema(description = "备注", example = "第 1 块")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

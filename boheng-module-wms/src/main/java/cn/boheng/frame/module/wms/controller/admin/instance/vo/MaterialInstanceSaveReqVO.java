package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 物料实例 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 创建/修改 Request VO")
@Data
public class MaterialInstanceSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "实例编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE96-000123")
    @NotEmpty(message = "实例编码不能为空")
    @Size(max = 64, message = "实例编码长度不能超过 64 个字符")
    private String instanceCode;

    @Schema(description = "实例名称", example = "第 1 块 96 孔板")
    @Size(max = 128, message = "实例名称长度不能超过 128 个字符")
    private String instanceName;

    @Schema(description = "条码", example = "6901234567890")
    @Size(max = 128, message = "条码长度不能超过 128 个字符")
    private String barcode;

    @Schema(description = "容器类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "容器类型编号不能为空")
    private Long containerTypeId;

    @Schema(description = "容器类型编码（冗余）", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE_96_WELL")
    @NotEmpty(message = "容器类型编码（冗余）不能为空")
    @Size(max = 64, message = "容器类型编码（冗余）长度不能超过 64 个字符")
    private String containerTypeCode;

    @Schema(description = "父实例编号（为空表示顶层）", example = "512")
    private Long parentInstanceId;

    @Schema(description = "在父容器中的位置", example = "A1")
    @Size(max = 32, message = "在父容器中的位置长度不能超过 32 个字符")
    private String parentPositionCode;

    @Schema(description = "物化路径", example = "/512/1024")
    @Size(max = 512, message = "物化路径长度不能超过 512 个字符")
    private String instancePath;

    @Schema(description = "根实例编号", example = "512")
    private Long rootInstanceId;

    @Schema(description = "落位槽位编号（仅顶层实例）", example = "2048")
    private Long rootSlotId;

    @Schema(description = "落位槽位编码（冗余）", example = "SLOT_RACK_A01_R1C1")
    @Size(max = 64, message = "落位槽位编码（冗余）长度不能超过 64 个字符")
    private String rootSlotCode;

    @Schema(description = "内容物定义编号（CARRIER 与空容器为空）", example = "2048")
    private Long contentDefId;

    @Schema(description = "内容物编码（冗余）", example = "PH-BUFFER-7")
    @Size(max = 64, message = "内容物编码（冗余）长度不能超过 64 个字符")
    private String contentDefCode;

    @Schema(description = "内容物类型快照（CARRIER 为空；空容器为 EMPTY）", example = "BUFFER")
    @Size(max = 32, message = "内容物类型快照（CARRIER 为空；空容器为 EMPTY）长度不能超过 32 个字符")
    private String contentType;

    @Schema(description = "当前体积（μL）", example = "200.00")
    private BigDecimal currentVolUl;

    @Schema(description = "当前数量（个）", example = "1")
    private Integer currentCount;

    @Schema(description = "扩展数据（JSON，实例级覆盖描述）", example = "JSON 对象，如 concentration")
    @Size(max = 512, message = "扩展数据（JSON，实例级覆盖描述）长度不能超过 512 个字符")
    private String extData;

    @Schema(description = "实例状态：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃", requiredMode = Schema.RequiredMode.REQUIRED, example = "AVAILABLE")
    @NotEmpty(message = "实例状态：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃不能为空")
    @Size(max = 32, message = "实例状态：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃长度不能超过 32 个字符")
    private String instanceStatus;

    @Schema(description = "备注", example = "第 1 块")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;
}

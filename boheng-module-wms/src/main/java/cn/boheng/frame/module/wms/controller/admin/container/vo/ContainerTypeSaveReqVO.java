package cn.boheng.frame.module.wms.controller.admin.container.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import cn.boheng.frame.framework.common.enums.CommonStatusEnum;
import cn.boheng.frame.framework.common.validation.InEnum;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 容器类型 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 容器类型 创建/修改 Request VO")
@Data
public class ContainerTypeSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "容器类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE_96_WELL")
    @NotEmpty(message = "容器类型编码不能为空")
    @Size(max = 64, message = "容器类型编码长度不能超过 64 个字符")
    private String typeCode;

    @Schema(description = "容器类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "96 孔板")
    @NotEmpty(message = "容器类型名称不能为空")
    @Size(max = 128, message = "容器类型名称长度不能超过 128 个字符")
    private String typeName;

    @Schema(description = "图片地址（可空，前端展示默认图）", example = "https://xxx.png")
    @Size(max = 512, message = "图片地址长度不能超过 512 个字符")
    private String imageUrl;

    @Schema(description = "物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE")
    @NotEmpty(message = "物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他不能为空")
    @Size(max = 32, message = "物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他长度不能超过 32 个字符")
    private String category;

    @Schema(description = "层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位", requiredMode = Schema.RequiredMode.REQUIRED, example = "CARRIER")
    @NotEmpty(message = "层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位不能为空")
    @Size(max = 32, message = "层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位长度不能超过 32 个字符")
    private String hierarchyRole;

    @Schema(description = "最大容积（μL）", example = "300.00")
    private BigDecimal maxVolUl;

    @Schema(description = "位数（仅 CARRIER）", example = "96")
    private Integer wellCount;

    @Schema(description = "行数（仅 CARRIER）", example = "8")
    private Integer wellRows;

    @Schema(description = "列数（仅 CARRIER）", example = "12")
    private Integer wellCols;

    @Schema(description = "位置命名规则：ROW_COL/SEQ/ROW_COL_LAYER/NONE", requiredMode = Schema.RequiredMode.REQUIRED, example = "ROW_COL")
    @NotEmpty(message = "位置命名规则：ROW_COL/SEQ/ROW_COL_LAYER/NONE不能为空")
    @Size(max = 16, message = "位置命名规则：ROW_COL/SEQ/ROW_COL_LAYER/NONE长度不能超过 16 个字符")
    private String positionNaming;

    @Schema(description = "承载的子单元类型编码（仅 CARRIER）", example = "WELL_STANDARD")
    @Size(max = 64, message = "承载的子单元类型编码（仅 CARRIER）长度不能超过 64 个字符")
    private String childTypeCode;

    @Schema(description = "其他规格参数（JSON）", example = "JSON 对象，如 color")
    @Size(max = 1024, message = "其他规格参数（JSON）长度不能超过 1024 个字符")
    private String specJson;

    @Schema(description = "是否允许被装入其他容器", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否允许被装入其他容器不能为空")
    private Boolean nestable;

    @Schema(description = "使用类型：REUSABLE 周转复用/DISPOSABLE 一次性", requiredMode = Schema.RequiredMode.REQUIRED, example = "REUSABLE")
    @NotEmpty(message = "使用类型：REUSABLE 周转复用/DISPOSABLE 一次性不能为空")
    @Size(max = 16, message = "使用类型：REUSABLE 周转复用/DISPOSABLE 一次性长度不能超过 16 个字符")
    private String usageType;

    @Schema(description = "复用次数上限（为空不限）", example = "100")
    private Integer lifeCycles;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "修改状态必须是 {value}")
    private Integer status;

    @Schema(description = "备注", example = "标准 96 孔板")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;
}

package cn.boheng.frame.module.wms.controller.admin.container.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/**
 * 容器类型 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 容器类型 Response VO")
@Data
public class ContainerTypeRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "容器类型编码", example = "PLATE_96_WELL")
    private String typeCode;

    @Schema(description = "容器类型名称", example = "96 孔板")
    private String typeName;

    @Schema(description = "图片地址（可空，前端展示默认图）", example = "https://xxx.png")
    private String imageUrl;

    @Schema(description = "物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他", example = "PLATE")
    private String category;

    @Schema(description = "层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位", example = "CARRIER")
    private String hierarchyRole;

    @Schema(description = "最大容积（μL）", example = "300.00")
    private BigDecimal maxVolUl;

    @Schema(description = "位数（仅 CARRIER）", example = "96")
    private Integer wellCount;

    @Schema(description = "行数（仅 CARRIER）", example = "8")
    private Integer wellRows;

    @Schema(description = "列数（仅 CARRIER）", example = "12")
    private Integer wellCols;

    @Schema(description = "位置命名规则：ROW_COL/SEQ/ROW_COL_LAYER/NONE", example = "ROW_COL")
    private String positionNaming;

    @Schema(description = "承载的子单元类型编码（仅 CARRIER）", example = "WELL_STANDARD")
    private String childTypeCode;

    @Schema(description = "其他规格参数（JSON）", example = "JSON 对象，如 color")
    private String specJson;

    @Schema(description = "是否允许被装入其他容器", example = "true")
    private Boolean nestable;

    @Schema(description = "使用类型：REUSABLE 周转复用/DISPOSABLE 一次性", example = "REUSABLE")
    private String usageType;

    @Schema(description = "复用次数上限（为空不限）", example = "100")
    private Integer lifeCycles;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "标准 96 孔板")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

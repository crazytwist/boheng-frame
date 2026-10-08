package cn.boheng.frame.module.wms.controller.admin.content.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容物定义 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 内容物定义 Response VO")
@Data
public class ContentDefRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "内容物编码", example = "PH-BUFFER-7")
    private String contentCode;

    @Schema(description = "内容物名称", example = "pH7 标准缓冲液")
    private String contentName;

    @Schema(description = "图片地址（可空，前端展示默认图）", example = "https://xxx.png")
    private String imageUrl;

    @Schema(description = "内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他", example = "BUFFER")
    private String contentType;

    @Schema(description = "计量单位：UL/ML/MG/G/COUNT", example = "ML")
    private String unit;

    @Schema(description = "供应商名称", example = "某生物科技")
    private String supplier;

    @Schema(description = "供应商货号", example = "CAT-0001")
    private String catalogNo;

    @Schema(description = "CAS 号", example = "9002-93-1")
    private String casNo;

    @Schema(description = "标准浓度描述", example = "pH7.0")
    private String concentration;

    @Schema(description = "存储条件：RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融", example = "RT")
    private String storageCond;

    @Schema(description = "保质期（天，为空不限）", example = "365")
    private Integer shelfLifeDays;

    @Schema(description = "开封后有效期（天，为空不限）", example = "30")
    private Integer openLifeDays;

    @Schema(description = "危险品等级：NONE/LOW/MEDIUM/HIGH/FLAMMABLE/TOXIC", example = "NONE")
    private String hazardLevel;

    @Schema(description = "是否 FEFO 出库", example = "true")
    private Boolean fefo;

    @Schema(description = "其他规格参数（JSON）", example = "JSON 对象，如 purity")
    private String specJson;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "常规缓冲液")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

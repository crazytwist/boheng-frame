package cn.boheng.frame.module.wms.controller.admin.content.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import cn.boheng.frame.framework.common.enums.CommonStatusEnum;
import cn.boheng.frame.framework.common.validation.InEnum;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 内容物定义 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 内容物定义 创建/修改 Request VO")
@Data
public class ContentDefSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "内容物编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PH-BUFFER-7")
    @NotEmpty(message = "内容物编码不能为空")
    @Size(max = 64, message = "内容物编码长度不能超过 64 个字符")
    private String contentCode;

    @Schema(description = "内容物名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "pH7 标准缓冲液")
    @NotEmpty(message = "内容物名称不能为空")
    @Size(max = 128, message = "内容物名称长度不能超过 128 个字符")
    private String contentName;

    @Schema(description = "图片地址（可空，前端展示默认图）", example = "https://xxx.png")
    @Size(max = 512, message = "图片地址长度不能超过 512 个字符")
    private String imageUrl;

    @Schema(description = "内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他", requiredMode = Schema.RequiredMode.REQUIRED, example = "BUFFER")
    @NotEmpty(message = "内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他不能为空")
    @Size(max = 32, message = "内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他长度不能超过 32 个字符")
    private String contentType;

    @Schema(description = "计量单位：UL/ML/MG/G/COUNT", requiredMode = Schema.RequiredMode.REQUIRED, example = "ML")
    @NotEmpty(message = "计量单位：UL/ML/MG/G/COUNT不能为空")
    @Size(max = 16, message = "计量单位：UL/ML/MG/G/COUNT长度不能超过 16 个字符")
    private String unit;

    @Schema(description = "供应商名称", example = "某生物科技")
    @Size(max = 128, message = "供应商名称长度不能超过 128 个字符")
    private String supplier;

    @Schema(description = "供应商货号", example = "CAT-0001")
    @Size(max = 64, message = "供应商货号长度不能超过 64 个字符")
    private String catalogNo;

    @Schema(description = "CAS 号", example = "9002-93-1")
    @Size(max = 32, message = "CAS 号长度不能超过 32 个字符")
    private String casNo;

    @Schema(description = "标准浓度描述", example = "pH7.0")
    @Size(max = 64, message = "标准浓度描述长度不能超过 64 个字符")
    private String concentration;

    @Schema(description = "存储条件：RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融", example = "RT")
    @Size(max = 32, message = "存储条件：RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融长度不能超过 32 个字符")
    private String storageCond;

    @Schema(description = "保质期（天，为空不限）", example = "365")
    private Integer shelfLifeDays;

    @Schema(description = "开封后有效期（天，为空不限）", example = "30")
    private Integer openLifeDays;

    @Schema(description = "危险品等级：NONE/LOW/MEDIUM/HIGH/FLAMMABLE/TOXIC", example = "NONE")
    @Size(max = 16, message = "危险品等级：NONE/LOW/MEDIUM/HIGH/FLAMMABLE/TOXIC长度不能超过 16 个字符")
    private String hazardLevel;

    @Schema(description = "是否 FEFO 出库", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否 FEFO 出库不能为空")
    private Boolean fefo;

    @Schema(description = "其他规格参数（JSON）", example = "JSON 对象，如 purity")
    @Size(max = 1024, message = "其他规格参数（JSON）长度不能超过 1024 个字符")
    private String specJson;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "修改状态必须是 {value}")
    private Integer status;

    @Schema(description = "备注", example = "常规缓冲液")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;
}

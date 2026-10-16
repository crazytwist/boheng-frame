package cn.boheng.frame.module.wms.controller.admin.sample.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 样本 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 样本 创建/修改 Request VO")
@Data
public class SampleInfoSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "样本业务编号（条码/二维码）", requiredMode = Schema.RequiredMode.REQUIRED, example = "SP-20261011-001")
    @NotEmpty(message = "样本业务编号不能为空")
    @Size(max = 128, message = "样本业务编号长度不能超过 128 个字符")
    private String sampleId;

    @Schema(description = "父样本编号（NULL=原始样本）", example = "SP-20261011-001")
    private String parentSampleId;

    @Schema(description = "根样本编号（原始样本=自身）", example = "SP-20261011-001")
    private String rootSampleId;

    @Schema(description = "谱系代数", example = "0")
    private Integer generation;

    @Schema(description = "衍生类型: ORIGINAL/SPLIT/MERGE/ALIQUOT/ADD_REAGENT", example = "ORIGINAL")
    private String deriveType;

    @Schema(description = "样本类型（字典 wms_sample_type）", example = "WATER")
    @Size(max = 64, message = "样本类型长度不能超过 64 个字符")
    private String sampleType;

    @Schema(description = "样本名称/描述", example = "地表水样 A1")
    @Size(max = 128, message = "样本名称长度不能超过 128 个字符")
    private String sampleName;

    @Schema(description = "内容物定义编号（样本是什么物质）", example = "2048")
    private Long contentDefId;

    @Schema(description = "内容物编码（冗余）", example = "SURFACE-WATER")
    @Size(max = 64, message = "内容物编码长度不能超过 64 个字符")
    private String contentDefCode;

    @Schema(description = "期望容器类型编码", example = "TUBE_EP_15ML")
    @Size(max = 64, message = "容器类型编码长度不能超过 64 个字符")
    private String containerTypeCode;

    @Schema(description = "初始体积（μL）", example = "500.00")
    private BigDecimal initialVolUl;

    @Schema(description = "实验参数（JSON，流程步骤执行时读）", example = "{\"targetPh\":7.2}")
    private String experimentParams;

    @Schema(description = "扩展属性（JSON）", example = "{\"color\":\"透明\"}")
    private String extData;

    @Schema(description = "样本状态: REGISTERED/LOADED/IN_PROCESS/SPLIT/COMPLETED/ARCHIVED/REJECTED/LOST", requiredMode = Schema.RequiredMode.REQUIRED, example = "REGISTERED")
    @NotEmpty(message = "样本状态不能为空")
    @Size(max = 32, message = "样本状态长度不能超过 32 个字符")
    private String status;

    @Schema(description = "当前流程执行编号", example = "exe_2109296770570190800")
    private String currentExecutionId;

    @Schema(description = "当前所在步骤节点编号", example = "pretreat")
    private String currentNodeId;

    @Schema(description = "样本批次号", example = "B20261011")
    @Size(max = 64, message = "样本批次号长度不能超过 64 个字符")
    private String batchNo;

    @Schema(description = "检测单/委托单号", example = "ORD-20261011-001")
    @Size(max = 64, message = "检测单/委托单号长度不能超过 64 个字符")
    private String orderNo;

    @Schema(description = "优先级: 0 普通/1 加急/2 特急", example = "0")
    private Integer priority;

    @Schema(description = "来源: MANUAL/IMPORT/LIS/DEVICE", example = "MANUAL")
    @Size(max = 32, message = "来源长度不能超过 32 个字符")
    private String source;

    @Schema(description = "外部系统 ID", example = "LIS-20261011-001")
    @Size(max = 64, message = "外部系统 ID 长度不能超过 64 个字符")
    private String externalId;

    @Schema(description = "采样时间")
    private LocalDateTime collectedAt;

    @Schema(description = "收样时间")
    private LocalDateTime receivedAt;

    @Schema(description = "样本有效期")
    private LocalDateTime expireTime;

    @Schema(description = "备注", example = "加急样本")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;
}

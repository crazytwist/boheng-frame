package cn.boheng.frame.module.wms.controller.admin.sample.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 样本 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 样本 Response VO")
@Data
public class SampleInfoRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "样本业务编号", example = "SP-20261011-001")
    private String sampleId;

    @Schema(description = "父样本编号", example = "SP-20261011-001")
    private String parentSampleId;

    @Schema(description = "根样本编号", example = "SP-20261011-001")
    private String rootSampleId;

    @Schema(description = "谱系代数", example = "0")
    private Integer generation;

    @Schema(description = "衍生类型", example = "ORIGINAL")
    private String deriveType;

    @Schema(description = "样本类型", example = "WATER")
    private String sampleType;

    @Schema(description = "样本名称", example = "地表水样 A1")
    private String sampleName;

    @Schema(description = "内容物定义编号", example = "2048")
    private Long contentDefId;

    @Schema(description = "内容物编码", example = "SURFACE-WATER")
    private String contentDefCode;

    @Schema(description = "期望容器类型编码", example = "TUBE_EP_15ML")
    private String containerTypeCode;

    @Schema(description = "初始体积（μL）", example = "500.00")
    private BigDecimal initialVolUl;

    @Schema(description = "实验参数（JSON）", example = "{\"targetPh\":7.2}")
    private String experimentParams;

    @Schema(description = "扩展属性（JSON）", example = "{\"color\":\"透明\"}")
    private String extData;

    @Schema(description = "样本状态", example = "REGISTERED")
    private String status;

    @Schema(description = "当前流程执行编号", example = "exe_2109296770570190800")
    private String currentExecutionId;

    @Schema(description = "当前所在步骤节点编号", example = "pretreat")
    private String currentNodeId;

    @Schema(description = "样本批次号", example = "B20261011")
    private String batchNo;

    @Schema(description = "检测单/委托单号", example = "ORD-20261011-001")
    private String orderNo;

    @Schema(description = "优先级", example = "0")
    private Integer priority;

    @Schema(description = "来源", example = "MANUAL")
    private String source;

    @Schema(description = "外部系统 ID", example = "LIS-20261011-001")
    private String externalId;

    @Schema(description = "采样时间")
    private LocalDateTime collectedAt;

    @Schema(description = "收样时间")
    private LocalDateTime receivedAt;

    @Schema(description = "样本有效期")
    private LocalDateTime expireTime;

    @Schema(description = "备注", example = "加急样本")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

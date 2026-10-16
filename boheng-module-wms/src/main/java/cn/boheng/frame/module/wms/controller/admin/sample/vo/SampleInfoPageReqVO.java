package cn.boheng.frame.module.wms.controller.admin.sample.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 样本 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 样本 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SampleInfoPageReqVO extends PageParam {

    @Schema(description = "样本业务编号", example = "SP-20261011-001")
    private String sampleId;

    @Schema(description = "样本名称", example = "地表水样")
    private String sampleName;

    @Schema(description = "父样本编号", example = "SP-20261011-001")
    private String parentSampleId;

    @Schema(description = "根样本编号", example = "SP-20261011-001")
    private String rootSampleId;

    @Schema(description = "衍生类型", example = "ORIGINAL")
    private String deriveType;

    @Schema(description = "样本类型（字典 wms_sample_type）", example = "WATER")
    private String sampleType;

    @Schema(description = "内容物定义编号", example = "2048")
    private Long contentDefId;

    @Schema(description = "期望容器类型编码", example = "TUBE_EP_15ML")
    private String containerTypeCode;

    @Schema(description = "样本状态", example = "REGISTERED")
    private String status;

    @Schema(description = "当前流程执行编号", example = "exe_2109296770570190800")
    private String currentExecutionId;

    @Schema(description = "样本批次号", example = "B20261011")
    private String batchNo;

    @Schema(description = "检测单/委托单号", example = "ORD-20261011-001")
    private String orderNo;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}

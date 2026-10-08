package cn.boheng.frame.module.wms.controller.admin.content.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 内容物定义 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 内容物定义 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContentDefPageReqVO extends PageParam {

    @Schema(description = "内容物编码", example = "PH-BUFFER-7")
    private String contentCode;

    @Schema(description = "内容物名称", example = "pH7 标准缓冲液")
    private String contentName;

    @Schema(description = "内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他", example = "BUFFER")
    private String contentType;

    @Schema(description = "CAS 号", example = "9002-93-1")
    private String casNo;

    @Schema(description = "存储条件：RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融", example = "RT")
    private String storageCond;

    @Schema(description = "危险品等级：NONE/LOW/MEDIUM/HIGH/FLAMMABLE/TOXIC", example = "NONE")
    private String hazardLevel;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}

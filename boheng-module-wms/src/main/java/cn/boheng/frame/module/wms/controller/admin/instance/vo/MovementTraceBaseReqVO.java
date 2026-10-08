package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 物料操作（上下架 / 转移 / 消耗）公共追溯字段 Request VO
 *
 * <p>这些字段不改变业务语义，只决定流水怎么记，因此抽成公共基类避免每个 VO 重复一遍。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料操作公共追溯字段")
@Data
public class MovementTraceBaseReqVO {

    @Schema(description = "备注 / 操作原因（下架不强制填写）", example = "实验消耗")
    private String remark;

    @Schema(description = "业务来源：MANUAL 手工/TASK 调度任务/DEVICE 设备/IMPORT 导入；不传默认 MANUAL", example = "MANUAL")
    private String bizSource;

    @Schema(description = "操作者类型：USER 人工/DEVICE 设备/AUTO 自动；不传默认 USER", example = "USER")
    private String operatorType;

    @Schema(description = "关联业务类型：TASK 调度任务/ORDER 单据/API 外部调用/CHECK 盘点", example = "TASK")
    private String refType;

    @Schema(description = "关联业务编号（跨模块只认编码，不建外键）", example = "TASK-001")
    private String refId;

    @Schema(description = "关联业务单号", example = "TK202609260001")
    private String refNo;

    @Schema(description = "幂等键（外部模块调用防重复过账；同一租户内重复传值只会生效一次）", example = "task-001-consume-1")
    private String idempotentKey;

    @Schema(description = "业务操作时间（不传取当前时间；支持补录历史）")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime operateTime;

    @Schema(description = "操作批次号（一次批量操作的多条明细共享；不传由后端生成）", example = "OP-20260926-0001")
    private String operationId;

}

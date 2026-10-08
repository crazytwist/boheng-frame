package cn.boheng.frame.module.wms.controller.admin.movement.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 物料流水 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料流水 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialMovementPageReqVO extends PageParam {

    @Schema(description = "流水类型：CREATE 建账/PUT_IN 上架/TAKE_OUT 下架/MOVE 转移/CONSUME 消耗/STATUS_CHANGE 状态变更/RESERVE 预留/RELEASE 释放预留/ADJUST 冲正", example = "PUT_IN")
    private String movementType;

    @Schema(description = "业务来源：MANUAL 手工/TASK 调度任务/DEVICE 设备/IMPORT 导入", example = "MANUAL")
    private String bizSource;

    @Schema(description = "操作批次号", example = "OP-20260926-0001")
    private String operationId;

    @Schema(description = "实例编号（精确匹配流水主体）", example = "1024")
    private Long instanceId;

    @Schema(description = "实例编码（模糊匹配）", example = "INST-580689")
    private String instanceCode;

    @Schema(description = "根实例编号（查某个顶层容器的整树流水）", example = "1024")
    private Long rootInstanceId;

    @Schema(description = "源槽位编号", example = "2048")
    private Long fromSlotId;

    @Schema(description = "目标槽位编号", example = "2048")
    private Long toSlotId;

    @Schema(description = "槽位编号（源或目标任一命中，用于「本槽位进出明细」）", example = "2048")
    private Long slotId;

    @Schema(description = "源区域编码", example = "Zone_Rack_Reagent")
    private String fromZoneCode;

    @Schema(description = "目标区域编码", example = "Zone_Rack_Reagent")
    private String toZoneCode;

    @Schema(description = "区域编码（源或目标任一命中，用于按区域筛流水）", example = "Zone_Rack_Reagent")
    private String zoneCode;

    @Schema(description = "内容物编码", example = "PH-BUFFER-7")
    private String contentDefCode;

    @Schema(description = "操作者类型：USER 人工/DEVICE 设备/AUTO 自动", example = "USER")
    private String operatorType;

    @Schema(description = "操作人", example = "1")
    private String operator;

    @Schema(description = "关联业务类型", example = "TASK")
    private String refType;

    @Schema(description = "关联业务编号", example = "TASK-001")
    private String refId;

    @Schema(description = "操作时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] operateTime;

}

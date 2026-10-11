package cn.boheng.frame.module.flow.controller.app.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备任务。机器端认领时下发，管理后台监控时回显。
 */
@Schema(description = "管理后台 / 机器端 - 设备任务 Response VO")
@Data
public class FlowDeviceTaskRespVO {

    @Schema(description = "任务号", example = "task_1812345678901234567")
    private String taskNo;

    @Schema(description = "所属运行编号", example = "1")
    private Long executionId;

    @Schema(description = "节点编码", example = "read-plate")
    private String nodeId;

    @Schema(description = "尝试序号", example = "1")
    private Integer attempt;

    @Schema(description = "循环轮次", example = "0")
    private Integer iterationNo;

    @Schema(description = "目标设备编码", example = "TECAN-01")
    private String deviceCode;

    @Schema(description = "任务类型 / 动作编码", example = "read-plate")
    private String actionCode;

    @Schema(description = "任务参数 JSON", example = "{\"plateId\":\"PLT-001\"}")
    private String paramsJson;

    @Schema(description = "状态：PENDING / CLAIMED / DONE / FAILED / CANCELLED", example = "CLAIMED")
    private String status;

    @Schema(description = "认领的机器端标识", example = "robot-arm-3")
    private String claimedBy;

    @Schema(description = "认领时间")
    private LocalDateTime claimedAt;

    @Schema(description = "完成结果 JSON", example = "{\"od450\":[0.1,0.2]}")
    private String resultJson;

    @Schema(description = "失败说明")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

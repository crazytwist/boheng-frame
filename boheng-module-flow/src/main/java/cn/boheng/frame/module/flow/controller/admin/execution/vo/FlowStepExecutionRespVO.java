package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点执行的一行（时间线）。重试是新的行，循环按轮次展开。
 */
@Schema(description = "管理后台 - 流程节点执行 Response VO")
@Data
public class FlowStepExecutionRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "运行编号")
    private Long executionId;

    @Schema(description = "节点编码")
    private String nodeId;

    @Schema(description = "启动时冻结的步骤类型")
    private String stepType;

    @Schema(description = "循环轮次，从 0 开始")
    private Integer iterationNo;

    @Schema(description = "本轮的尝试序号，从 1 开始")
    private Integer attempt;

    @Schema(description = "状态：PENDING / RUNNING / WAITING / SUCCESS / FAILED / DEAD / SKIPPED / CANCELLED")
    private String status;

    @Schema(description = "跳过原因：BRANCH 出口未选中 / FAILURE 前置失败 / UPSTREAM 上游连带")
    private String skipReason;

    @Schema(description = "等待什么：DEVICE_CALLBACK / MANUAL / TIMER / CHILD")
    private String waitingFor;

    @Schema(description = "等待引用：设备命令号、到期时刻等")
    private String waitRef;

    @Schema(description = "设备任务状态（device.task 节点专用）：PENDING / CLAIMED / DONE / FAILED / CANCELLED")
    private String deviceTaskStatus;

    @Schema(description = "关联的设备命令编号")
    private Long commandId;

    @Schema(description = "关联的设备命令号")
    private String commandNo;

    @Schema(description = "路由节点选中的出口")
    private String selectedBranch;

    @Schema(description = "输入快照 JSON")
    private String inputSnapshot;

    @Schema(description = "输出 JSON")
    private String outputData;

    @Schema(description = "失败码")
    private String errorCode;

    @Schema(description = "失败说明")
    private String errorMessage;

    @Schema(description = "入队时间")
    private LocalDateTime queuedAt;

    @Schema(description = "开始执行时间")
    private LocalDateTime startedAt;

    @Schema(description = "终态时间")
    private LocalDateTime finishedAt;

    @Schema(description = "等待截止时刻")
    private LocalDateTime deadlineAt;

}

package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运行详情与列表的一行。详情时附带节点时间线和分支决策。
 */
@Schema(description = "管理后台 - 流程运行 Response VO")
@Data
public class FlowExecutionRespVO {

    @Schema(description = "运行编号")
    private Long id;

    @Schema(description = "运行业务号")
    private String executionNo;

    @Schema(description = "流程编码")
    private String pipelineKey;

    @Schema(description = "流程版本")
    private Integer version;

    @Schema(description = "流程版本编号")
    private Long definitionId;

    @Schema(description = "启动时冻结的定义指纹")
    private String checksum;

    @Schema(description = "状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED")
    private String status;

    @Schema(description = "触发方式：MANUAL / SCHEDULE / CHILD")
    private String triggerType;

    @Schema(description = "触发人")
    private String triggeredBy;

    @Schema(description = "业务关联键")
    private String businessKey;

    @Schema(description = "父运行编号。子流程扇出时非空")
    private Long parentExecutionId;

    @Schema(description = "父节点编码")
    private String parentNodeId;

    @Schema(description = "父节点尝试序号")
    private Integer parentAttempt;

    @Schema(description = "FANOUT 扇出的第几组")
    private Integer parentGroupIndex;

    @Schema(description = "启动入参 JSON")
    private String inputParams;

    @Schema(description = "运行产出 JSON")
    private String outputData;

    @Schema(description = "启动时间")
    private LocalDateTime startedAt;

    @Schema(description = "终态时间")
    private LocalDateTime finishedAt;

    @Schema(description = "失败码")
    private String errorCode;

    @Schema(description = "失败说明")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "节点时间线。仅详情接口返回")
    private List<FlowStepExecutionRespVO> steps;

    @Schema(description = "分支决策记录。仅详情接口返回")
    private List<FlowBranchDecisionRespVO> branchDecisions;

}

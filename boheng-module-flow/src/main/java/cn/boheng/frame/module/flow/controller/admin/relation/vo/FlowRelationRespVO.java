package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 关联列表的一行。步骤上的设备和动作一并带出，列表不用再查一次。
 */
@Schema(description = "管理后台 - 流程步骤 Response VO")
@Data
public class FlowRelationRespVO {

    @Schema(description = "关联编号")
    private Long id;

    @Schema(description = "流程版本编号")
    private Long definitionId;

    @Schema(description = "流程编码")
    private String pipelineKey;

    @Schema(description = "流程名称")
    private String definitionName;

    @Schema(description = "流程版本")
    private Integer version;

    @Schema(description = "流程状态")
    private String definitionStatus;

    @Schema(description = "顺序，从 0 开始")
    private Integer sortNo;

    @Schema(description = "节点编码")
    private String nodeId;

    @Schema(description = "步骤编号")
    private Long stepId;

    @Schema(description = "步骤编码")
    private String stepKey;

    @Schema(description = "步骤名称")
    private String stepName;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "等待秒数")
    private Integer timeoutSec;

    @Schema(description = "尝试次数")
    private Integer maxAttempts;

}

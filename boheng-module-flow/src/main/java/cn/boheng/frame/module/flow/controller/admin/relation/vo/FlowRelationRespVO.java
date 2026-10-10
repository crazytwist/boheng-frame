package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 关联（节点）列表的一行。展示值是装配后的生效值：
 * 类型与配置按「节点级覆盖优先、回落步骤表」解析，编排字段原样带出。
 */
@Schema(description = "管理后台 - 流程节点 Response VO")
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

    @Schema(description = "节点名称。控制类节点用；挂能力时同步骤名称")
    private String name;

    @Schema(description = "步骤编号。控制类节点为空")
    private Long stepId;

    @Schema(description = "步骤编码")
    private String stepKey;

    @Schema(description = "步骤名称")
    private String stepName;

    @Schema(description = "生效的步骤类型", example = "device.command")
    private String stepType;

    @Schema(description = "生效的类型配置 JSON")
    private String typeConfig;

    @Schema(description = "前置节点编码")
    private List<String> dependsOn;

    @Schema(description = "汇合策略：ALL_SUCCESS / ANY_SUCCESS / ALL_DONE")
    private String joinPolicy;

    @Schema(description = "前置失败策略：FAIL / SKIP")
    private String joinFailurePolicy;

    @Schema(description = "出口映射（route 专用）")
    private Map<String, List<String>> branches;

    @Schema(description = "参数映射。来源 INPUT / NODE / LITERAL")
    private List<cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowMappingReqVO> mappings;

    @Schema(description = "等待秒数（生效值）")
    private Integer timeoutSec;

    @Schema(description = "尝试次数（生效值）")
    private Integer maxAttempts;

}

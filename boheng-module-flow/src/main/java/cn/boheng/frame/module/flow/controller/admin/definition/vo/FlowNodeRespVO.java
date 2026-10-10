package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 返回给编排页的一个节点。类型与配置是装配后的生效值，
 * 编排字段（依赖、汇合、分支）原样带出，画布按它复原图形。
 */
@Schema(description = "管理后台 - 流程节点 Response VO")
@Data
public class FlowNodeRespVO {

    @Schema(description = "关联编号")
    private Long id;

    @Schema(description = "步骤编号。控制类节点为空")
    private Long stepId;

    @Schema(description = "步骤编码。控制类节点为空")
    private String stepKey;

    @Schema(description = "节点编码")
    private String nodeId;

    @Schema(description = "节点名称")
    private String name;

    @Schema(description = "顺序，从 0 开始")
    private Integer sortNo;

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

    @Schema(description = "等待秒数（生效值）")
    private Integer timeoutSec;

    @Schema(description = "尝试次数（生效值）")
    private Integer maxAttempts;

    @Schema(description = "失败策略：FAIL_FAST / FAIL_SKIP")
    private String failStrategy;

    @Schema(description = "参数映射")
    private List<FlowMappingReqVO> mappings;

}

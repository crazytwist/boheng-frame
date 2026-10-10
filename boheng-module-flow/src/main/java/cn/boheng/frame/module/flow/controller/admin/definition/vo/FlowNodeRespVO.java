package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 返回给编排页的一步。依赖数组一并返回，方便核对服务端确实按顺序写了。
 */
@Schema(description = "管理后台 - 流程节点 Response VO")
@Data
public class FlowNodeRespVO {

    @Schema(description = "关联编号")
    private Long id;

    @Schema(description = "步骤编号")
    private Long stepId;

    @Schema(description = "步骤编码")
    private String stepKey;

    @Schema(description = "节点编码")
    private String nodeId;

    @Schema(description = "步骤名称")
    private String name;

    @Schema(description = "顺序，从 0 开始")
    private Integer sortNo;

    @Schema(description = "步骤类型")
    private String stepType;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "前置步骤编码")
    private List<String> dependsOn;

    @Schema(description = "等待秒数")
    private Integer timeoutSec;

    @Schema(description = "尝试次数")
    private Integer maxAttempts;

    @Schema(description = "失败策略")
    private String failStrategy;

    @Schema(description = "参数映射")
    private List<FlowMappingReqVO> mappings;

}

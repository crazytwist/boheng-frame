package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * route 节点的一次分支决策记录：选了哪条、激活了谁、当时的值。
 */
@Schema(description = "管理后台 - 流程分支决策 Response VO")
@Data
public class FlowBranchDecisionRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "运行编号")
    private Long executionId;

    @Schema(description = "路由节点编码")
    private String routeNodeId;

    @Schema(description = "节点的尝试序号")
    private Integer attempt;

    @Schema(description = "循环轮次")
    private Integer iterationNo;

    @Schema(description = "选中的出口名")
    private String selectedBranch;

    @Schema(description = "匹配语义：EXCLUSIVE / INCLUSIVE")
    private String matchMode;

    @Schema(description = "被激活的目标节点编码 JSON 数组")
    private String activatedTargets;

    @Schema(description = "决策快照 JSON：表达式文本 + 求值时的变量值")
    private String decisionData;

}

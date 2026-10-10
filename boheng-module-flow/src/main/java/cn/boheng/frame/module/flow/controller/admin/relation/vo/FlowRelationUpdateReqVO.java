package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowMappingReqVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑一个节点的编排形态：前置、汇合策略、分支出口、参数映射，
 * 以及控制类节点的名称与类型配置。每一步保存后草稿都保持结构合法。
 */
@Schema(description = "管理后台 - 流程节点更新 Request VO")
@Data
public class FlowRelationUpdateReqVO {

    @Schema(description = "关联编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "关联不能为空")
    private Long id;

    @Schema(description = "节点名称。控制类节点用")
    @Size(max = 128, message = "节点名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "节点级类型配置 JSON。控制类节点必填；挂能力时为节点覆盖，空则取步骤上的")
    @Size(max = 4096, message = "类型配置长度不能超过 4096 个字符")
    private String typeConfig;

    @Schema(description = "前置节点编码。空 = 无前置（起点）")
    private List<String> dependsOn;

    @Schema(description = "汇合策略：ALL_SUCCESS / ANY_SUCCESS / ALL_DONE", example = "ALL_SUCCESS")
    @Pattern(regexp = "^$|ALL_SUCCESS|ANY_SUCCESS|ALL_DONE", message = "汇合策略只能是 ALL_SUCCESS、ANY_SUCCESS 或 ALL_DONE")
    private String joinPolicy;

    @Schema(description = "前置失败策略：FAIL / SKIP", example = "FAIL")
    @Pattern(regexp = "^$|FAIL|SKIP", message = "前置失败策略只能是 FAIL 或 SKIP")
    private String joinFailurePolicy;

    @Schema(description = "出口映射（route 专用）：出口名 -> 目标节点编码列表")
    private Map<String, List<String>> branches = new LinkedHashMap<>();

    @Schema(description = "参数映射")
    @Valid
    private List<FlowMappingReqVO> mappings;

}

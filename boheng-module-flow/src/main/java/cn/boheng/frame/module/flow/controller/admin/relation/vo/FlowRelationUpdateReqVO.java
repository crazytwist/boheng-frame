package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowMappingReqVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Schema(description = "节点输出归一化映射：{输出字段: 上下文键}。空表清空")
    private Map<String, String> outputMapping;

    // ==================== 节点级运行配置（落 flow_node.run_config） ====================
    // 四个字段共用一套约定：null = 本字段不动（保持库里现值）；0 / 空串 = 清掉这层覆盖，回落步骤表。
    // 这样"编辑节点编号"这类请求不会顺手把已有的运行配置抹掉。

    @Schema(description = "节点级超时秒数。null=不改，0=清除覆盖（回落步骤表），上限 24 小时", example = "300")
    @Min(value = 0, message = "超时秒数不能为负")
    @Max(value = 86400, message = "超时秒数不能超过 86400 秒（24 小时）")
    private Integer timeoutSec;

    @Schema(description = "节点级最大尝试次数（含首次）。null=不改，0=清除覆盖", example = "3")
    @Min(value = 0, message = "尝试次数不能为负")
    @Max(value = 10, message = "尝试次数不能超过 10")
    private Integer maxAttempts;

    @Schema(description = "节点级失败策略：FAIL_FAST 快速失败 / FAIL_SKIP 跳过下游。null=不改，空串=清除覆盖",
            example = "FAIL_FAST")
    @Pattern(regexp = "^$|FAIL_FAST|FAIL_SKIP", message = "失败策略只能是 FAIL_FAST 或 FAIL_SKIP")
    private String failureStrategy;

    @Schema(description = "完成断言（Aviator 表达式）。执行器报成功后再过这道判断，不成立按失败结算并复用重试。"
            + "作用域 output.* / params.* / node.<nodeId>.* / input.*。null=不改，空串=清除断言",
            example = "output.weight >= 0.95")
    @Size(max = 1024, message = "完成断言长度不能超过 1024 个字符")
    private String completeWhen;

    @Schema(description = "画布横坐标。拖动节点时回写")
    private Integer x;

    @Schema(description = "画布纵坐标。拖动节点时回写")
    private Integer y;

}

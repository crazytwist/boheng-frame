package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 往流程草稿上加一个节点。两种形态：
 * 1. 挂能力：stepId 非空，类型与配置取自步骤表，nodeId 空则默认用步骤编码；
 * 2. 控制类节点：stepId 为空，stepType + typeConfig + name 必填（route / loop / subflow / timer.wait）。
 */
@Schema(description = "管理后台 - 流程节点新增 Request VO")
@Data
public class FlowRelationSaveReqVO {

    @Schema(description = "流程版本编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "流程不能为空")
    private Long definitionId;

    @Schema(description = "步骤编号（挂能力时填）。控制类节点不填")
    private Long stepId;

    @Schema(description = "节点编码。挂能力时空着用步骤编码；同一能力用两次时要另写一个")
    @Size(max = 32, message = "节点编码长度不能超过 32 个字符")
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{0,31}$", message = "节点编码须是小写字母开头，只含小写、数字和下划线")
    private String nodeId;

    @Schema(description = "节点名称。控制类节点必填；挂能力时忽略，展示用步骤名")
    @Size(max = 128, message = "节点名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "步骤类型。控制类节点必填（route / loop / subflow / timer.wait）", example = "route")
    @Size(max = 64, message = "步骤类型长度不能超过 64 个字符")
    private String stepType;

    @Schema(description = "类型配置 JSON。控制类节点必填，结构由类型决定")
    @Size(max = 4096, message = "类型配置长度不能超过 4096 个字符")
    private String typeConfig;

    @Schema(description = "前置节点编码。空 = 无前置（起点）")
    private List<String> dependsOn = new ArrayList<>();

}

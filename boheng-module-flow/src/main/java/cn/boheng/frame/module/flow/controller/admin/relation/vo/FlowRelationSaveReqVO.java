package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 把一条已有步骤挂到某个流程版本上。
 */
@Schema(description = "管理后台 - 流程步骤关联 Request VO")
@Data
public class FlowRelationSaveReqVO {

    @Schema(description = "流程版本编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "流程不能为空")
    private Long definitionId;

    @Schema(description = "步骤编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "步骤不能为空")
    private Long stepId;

    @Schema(description = "节点编码。空着时用步骤编码；同一步用两次时要另写一个")
    @Size(max = 32, message = "节点编码长度不能超过 32 个字符")
    @Pattern(regexp = "^$|^[a-z][a-z0-9_]{0,31}$", message = "节点编码须是小写字母开头，只含小写、数字和下划线")
    private String nodeId;

}

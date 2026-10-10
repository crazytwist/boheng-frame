package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存草稿的名称和说明。步骤不在这里提交，到流程步骤里关联。
 */
@Schema(description = "管理后台 - 流程定义保存 Request VO")
@Data
public class FlowDefinitionSaveReqVO {

    @Schema(description = "流程版本编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "流程编号不能为空")
    private Long id;

    @Schema(description = "流程名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "说明")
    @Size(max = 512, message = "说明长度不能超过 512 个字符")
    private String remark;

}

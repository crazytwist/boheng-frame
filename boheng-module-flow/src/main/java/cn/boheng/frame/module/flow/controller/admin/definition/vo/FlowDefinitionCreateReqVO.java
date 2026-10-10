package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建一条流程的第 1 版草稿。节点在编排页再加。
 */
@Schema(description = "管理后台 - 流程定义创建 Request VO")
@Data
public class FlowDefinitionCreateReqVO {

    @Schema(description = "流程编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "plate_read")
    @NotEmpty(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码长度不能超过 64 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$", message = "流程编码须是小写字母开头，只含小写、数字和下划线")
    private String pipelineKey;

    @Schema(description = "流程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "离心后读板")
    @NotEmpty(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "说明")
    @Size(max = 512, message = "说明长度不能超过 512 个字符")
    private String remark;

}

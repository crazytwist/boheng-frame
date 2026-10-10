package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工确认 / 拒绝一个等待人工的节点。
 */
@Schema(description = "管理后台 - 人工确认 Request VO")
@Data
public class FlowSignalManualReqVO {

    @Schema(description = "运行编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "运行不能为空")
    private Long executionId;

    @Schema(description = "节点编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "节点不能为空")
    @Size(max = 32, message = "节点编码长度不能超过 32 个字符")
    private String nodeId;

    @Schema(description = "true 确认放行，false 拒绝（节点按失败结算）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "确认结果不能为空")
    private Boolean approve;

    @Schema(description = "备注。拒绝时建议填写原因")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String comment;

}

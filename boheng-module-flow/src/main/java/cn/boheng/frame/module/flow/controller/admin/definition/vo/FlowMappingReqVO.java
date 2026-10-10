package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 一个参数的来源。三选一，由 source 决定后面哪个字段有意义。
 */
@Schema(description = "管理后台 - 流程参数映射 Request VO")
@Data
public class FlowMappingReqVO {

    @Schema(description = "参数名", requiredMode = Schema.RequiredMode.REQUIRED, example = "plateId")
    @NotEmpty(message = "参数名不能为空")
    @Size(max = 64, message = "参数名长度不能超过 64 个字符")
    private String param;

    @Schema(description = "来源：INPUT / NODE / LITERAL", requiredMode = Schema.RequiredMode.REQUIRED, example = "INPUT")
    @NotEmpty(message = "参数来源不能为空")
    private String source;

    @Schema(description = "字段名。来源是启动入参或上一步输出时填写", example = "plateId")
    @Size(max = 64, message = "字段名长度不能超过 64 个字符")
    private String field;

    @Schema(description = "上一步的步骤编码。来源是 NODE 时填写", example = "spin")
    @Size(max = 32, message = "步骤编码长度不能超过 32 个字符")
    private String nodeId;

    @Schema(description = "固定值。来源是 LITERAL 时填写")
    @Size(max = 512, message = "固定值长度不能超过 512 个字符")
    private String value;

}

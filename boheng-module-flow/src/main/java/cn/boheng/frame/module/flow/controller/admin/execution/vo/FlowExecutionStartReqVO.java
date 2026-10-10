package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 启动一次流程运行。只允许启动已发布版本。
 */
@Schema(description = "管理后台 - 流程运行启动 Request VO")
@Data
public class FlowExecutionStartReqVO {

    @Schema(description = "流程版本编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "流程版本不能为空")
    private Long definitionId;

    @Schema(description = "启动入参 JSON。参数映射里 INPUT 来源从这里取值", example = "{\"plateId\":\"PLT-001\"}")
    @Size(max = 4096, message = "启动入参长度不能超过 4096 个字符")
    private String inputParams;

    @Schema(description = "业务关联键，如实验单号", example = "EXP-20261010-001")
    @Size(max = 64, message = "业务关联键长度不能超过 64 个字符")
    private String businessKey;

}

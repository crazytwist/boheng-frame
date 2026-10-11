package cn.boheng.frame.module.flow.controller.app.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机器端回报一条设备任务的执行结果。
 */
@Schema(description = "机器端 - 设备任务回报 Request VO")
@Data
public class FlowDeviceTaskCompleteReqVO {

    @Schema(description = "任务号。认领时下发", requiredMode = Schema.RequiredMode.REQUIRED, example = "task_1812345678901234567")
    @NotBlank(message = "任务号不能为空")
    @Size(max = 64, message = "任务号长度不能超过 64 个字符")
    private String taskNo;

    @Schema(description = "回报令牌。认领时下发", requiredMode = Schema.RequiredMode.REQUIRED, example = "7f3a1b2c3d4e5f6a")
    @NotBlank(message = "回报令牌不能为空")
    @Size(max = 128, message = "回报令牌长度不能超过 128 个字符")
    private String callbackToken;

    @Schema(description = "true 执行成功，false 执行失败", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "执行结果不能为空")
    private Boolean success;

    @Schema(description = "成功结果 JSON，写进节点输出供下游参数映射取用")
    @Size(max = 4096, message = "结果 JSON 长度不能超过 4096 个字符")
    private String resultJson;

    @Schema(description = "失败说明")
    @Size(max = 512, message = "失败说明长度不能超过 512 个字符")
    private String errorMessage;

}

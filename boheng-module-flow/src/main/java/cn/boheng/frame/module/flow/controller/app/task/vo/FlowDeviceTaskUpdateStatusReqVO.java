package cn.boheng.frame.module.flow.controller.app.task.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 清华 {@code TaskUpdateCmd} 对齐：按 {@code listTaskByArea} 返回的 taskId 更新任务状态。
 */
@Data
@Schema(description = "机器端 - 按 taskId 更新设备任务状态")
public class FlowDeviceTaskUpdateStatusReqVO {

    @NotNull(message = "任务ID不能为空")
    @Schema(description = "任务ID（listTaskByArea 返回的 taskId）", name = "TaskId")
    @JsonProperty("TaskId")
    private Integer taskId;

    @NotNull(message = "任务状态不能为空")
    @Schema(description = "任务状态（清华 ExternalTaskStatusEnum code，如 1=运行中 2=已完成）", name = "Status")
    @JsonProperty("Status")
    private Integer status;

}

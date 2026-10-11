package cn.boheng.frame.module.flow.controller.app.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机器端认领一条待执行的设备任务。
 * 设备 / 任务类型两个维度都可空，机器端可以按设备拉、按任务类型拉、或组合拉。
 */
@Schema(description = "机器端 - 设备任务认领 Request VO")
@Data
public class FlowDeviceTaskClaimReqVO {

    @Schema(description = "目标设备编码。空 = 不限设备", example = "TECAN-01")
    @Size(max = 64, message = "设备编码长度不能超过 64 个字符")
    private String deviceCode;

    @Schema(description = "任务类型 / 动作编码。空 = 不限类型", example = "read-plate")
    @Size(max = 64, message = "任务类型长度不能超过 64 个字符")
    private String actionCode;

    @Schema(description = "机器端标识，写进认领记录用于追溯", requiredMode = Schema.RequiredMode.REQUIRED, example = "robot-arm-3")
    @NotBlank(message = "机器端标识不能为空")
    @Size(max = 64, message = "机器端标识长度不能超过 64 个字符")
    private String workerId;

}

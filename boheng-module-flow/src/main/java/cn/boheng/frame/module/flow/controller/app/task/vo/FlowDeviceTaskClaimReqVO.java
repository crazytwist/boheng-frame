package cn.boheng.frame.module.flow.controller.app.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机器端认领一条待执行的设备任务。
 * deviceCode 必传（机器端声明要操作哪台设备），平台按它的设备类型反查能力集，
 * 匹配「不限设备 + 我会做的类型」的任务；actionCode 可选，进一步限定任务类型。
 */
@Schema(description = "机器端 - 设备任务认领 Request VO")
@Data
public class FlowDeviceTaskClaimReqVO {

    @Schema(description = "机器端要操作的设备编码（必填）。平台按它的类型反查能力集来匹配任务", example = "WQ-COD-01")
    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码长度不能超过 64 个字符")
    private String deviceCode;

    @Schema(description = "任务类型 / 动作编码（可选）。空 = 取设备能力集内任意类型；非空 = 只要这一种", example = "MEASURE_COD")
    @Size(max = 64, message = "任务类型长度不能超过 64 个字符")
    private String actionCode;

    @Schema(description = "机器端标识，写进认领记录用于追溯", requiredMode = Schema.RequiredMode.REQUIRED, example = "robot-arm-3")
    @NotBlank(message = "机器端标识不能为空")
    @Size(max = 64, message = "机器端标识长度不能超过 64 个字符")
    private String workerId;

}

package cn.boheng.frame.module.device.controller.admin.simulate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 仿真 HTTP 配置的保存参数
 */
@Schema(description = "管理后台 - 仿真 HTTP 配置创建/修改")
@Data
public class DeviceSimulateProfileSaveReqVO {

    @Schema(description = "主键。修改时必填")
    private Long id;

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备编号不能为空")
    private Long deviceId;

    @Schema(description = "动作编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "READ_PLATE")
    @NotEmpty(message = "动作编码不能为空")
    private String actionCode;

    @Schema(description = "回收方式: SYNC 同步 / CALLBACK 设备回调 / POLL 轮询", requiredMode = Schema.RequiredMode.REQUIRED, example = "SYNC")
    @NotEmpty(message = "回收方式不能为空")
    private String returnMode;

    @Schema(description = "等待毫秒。CALLBACK 为受理后多久回调，POLL 为结果就绪前的时长", example = "0")
    private Integer asyncWaitMs;

    @Schema(description = "两次轮询之间的等待毫秒", example = "1000")
    private Integer pollIntervalMs;

    @Schema(description = "最大轮询次数", example = "30")
    private Integer pollMaxTimes;

    @Schema(description = "请求报文模板，支持 ${参数名}")
    private String requestTemplate;

    @Schema(description = "最终返回报文，支持 ${参数名} 和 ${jobId}")
    private String responseBody;

    @Schema(description = "轮询首次受理报文")
    private String ackBody;

    @Schema(description = "轮询未完成报文")
    private String pendingBody;

    @Schema(description = "启用状态: 0 启用 / 1 停用", example = "0")
    private Integer status;

}

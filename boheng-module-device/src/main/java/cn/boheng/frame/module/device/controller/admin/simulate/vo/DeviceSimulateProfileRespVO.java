package cn.boheng.frame.module.device.controller.admin.simulate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仿真 HTTP 配置的返回
 */
@Schema(description = "管理后台 - 仿真 HTTP 配置")
@Data
public class DeviceSimulateProfileRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "设备编号")
    private Long deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "回收方式: SYNC / CALLBACK / POLL")
    private String returnMode;

    @Schema(description = "等待毫秒")
    private Integer asyncWaitMs;

    @Schema(description = "轮询间隔毫秒")
    private Integer pollIntervalMs;

    @Schema(description = "最大轮询次数")
    private Integer pollMaxTimes;

    @Schema(description = "请求报文模板")
    private String requestTemplate;

    @Schema(description = "最终返回报文")
    private String responseBody;

    @Schema(description = "轮询首次受理报文")
    private String ackBody;

    @Schema(description = "轮询未完成报文")
    private String pendingBody;

    @Schema(description = "启用状态: 0 启用 / 1 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

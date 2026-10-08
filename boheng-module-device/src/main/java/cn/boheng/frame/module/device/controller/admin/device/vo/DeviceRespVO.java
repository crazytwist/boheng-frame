package cn.boheng.frame.module.device.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备台账 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备台账 Response VO")
@Data
public class DeviceRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "设备编码", example = "DEV-001")
    private String deviceCode;

    @Schema(description = "设备名称", example = "读板机 1 号")
    private String deviceName;

    @Schema(description = "设备图片地址", example = "https://xxx.png")
    private String imageUrl;

    @Schema(description = "设备类型编码", example = "PLATE_READER")
    private String deviceTypeCode;

    @Schema(description = "型号", example = "Infinite 200 PRO")
    private String model;

    @Schema(description = "厂商", example = "Tecan")
    private String vendor;

    @Schema(description = "序列号", example = "SN123456")
    private String serialNo;

    @Schema(description = "驱动类型", example = "SIMULATED")
    private String driverType;

    @Schema(description = "在线状态: ONLINE/OFFLINE/MAINTENANCE/FAULT", example = "ONLINE")
    private String status;

    @Schema(description = "接入方式: HTTP/MQTT/NODE_RED/SIMULATED", example = "SIMULATED")
    private String connectionType;

    @Schema(description = "接入地址", example = "http://192.168.1.100:8080")
    private String endpointUrl;

    @Schema(description = "MQTT 主题前缀", example = "device/DEV-001/")
    private String mqttTopicPrefix;

    @Schema(description = "仪器能否主动推送结果", example = "false")
    private Boolean callbackEnabled;

    @Schema(description = "默认轮询间隔(秒)", example = "5")
    private Integer pollIntervalSec;

    @Schema(description = "忙闲校验策略: AUTO/ALWAYS/NEVER", example = "AUTO")
    private String busyCheckPolicy;

    @Schema(description = "并发策略: EXCLUSIVE/DEVICE_QUEUED/PLATFORM_QUEUED", example = "EXCLUSIVE")
    private String concurrencyPolicy;

    @Schema(description = "允许同时在途命令数", example = "1")
    private Integer maxInflight;

    @Schema(description = "当前在途命令数(运行态)", example = "0")
    private Integer inflightCount;

    @Schema(description = "能力来源", example = "MANUAL")
    private String capabilitySource;

    @Schema(description = "属性当前值快照")
    private String telemetryJson;

    @Schema(description = "是否仿真模式", example = "true")
    private Boolean simulationMode;

    @Schema(description = "发现方式", example = "MANUAL")
    private String discoveryType;

    @Schema(description = "当前占用命令编号", example = "1024")
    private Long currentCommandId;

    @Schema(description = "锁持有方", example = "USER:1")
    private String lockHolder;

    @Schema(description = "锁类型", example = "COMMAND")
    private String lockType;

    @Schema(description = "锁获取时间")
    private LocalDateTime lockAcquiredTime;

    @Schema(description = "锁过期时间")
    private LocalDateTime lockExpireTime;

    @Schema(description = "加锁原因", example = "维护")
    private String lockReason;

    @Schema(description = "备注", example = "一楼实验室读板机")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

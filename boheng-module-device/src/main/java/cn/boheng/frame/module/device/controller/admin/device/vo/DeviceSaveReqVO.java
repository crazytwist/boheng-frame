package cn.boheng.frame.module.device.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 设备台账 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 设备台账 创建/修改 Request VO")
@Data
public class DeviceSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEV-001")
    @NotEmpty(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码长度不能超过 64 个字符")
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "读板机 1 号")
    @NotEmpty(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称长度不能超过 128 个字符")
    private String deviceName;

    @Schema(description = "设备图片地址（可空，前端展示默认图）", example = "https://xxx.png")
    @Size(max = 512, message = "设备图片地址长度不能超过 512 个字符")
    private String imageUrl;

    @Schema(description = "设备类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PLATE_READER")
    @NotEmpty(message = "设备类型编码不能为空")
    @Size(max = 64, message = "设备类型编码长度不能超过 64 个字符")
    private String deviceTypeCode;

    @Schema(description = "型号", example = "Infinite 200 PRO")
    @Size(max = 128, message = "型号长度不能超过 128 个字符")
    private String model;

    @Schema(description = "厂商", example = "Tecan")
    @Size(max = 128, message = "厂商长度不能超过 128 个字符")
    private String vendor;

    @Schema(description = "序列号", example = "SN123456")
    @Size(max = 128, message = "序列号长度不能超过 128 个字符")
    private String serialNo;

    @Schema(description = "驱动类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "SIMULATED")
    @NotEmpty(message = "驱动类型不能为空")
    @Size(max = 64, message = "驱动类型长度不能超过 64 个字符")
    private String driverType;

    @Schema(description = "在线状态: ONLINE 在线/OFFLINE 离线/MAINTENANCE 维护中/FAULT 故障", example = "OFFLINE")
    @NotEmpty(message = "在线状态不能为空")
    @Size(max = 32, message = "在线状态长度不能超过 32 个字符")
    private String status;

    @Schema(description = "接入方式: HTTP 直连/MQTT 直连/NODE_RED 经Node-RED/SIMULATED 仿真", example = "SIMULATED")
    @NotEmpty(message = "接入方式不能为空")
    @Size(max = 32, message = "接入方式长度不能超过 32 个字符")
    private String connectionType;

    @Schema(description = "接入地址", example = "http://192.168.1.100:8080")
    @Size(max = 512, message = "接入地址长度不能超过 512 个字符")
    private String endpointUrl;

    @Schema(description = "登录相对路径", example = "/api/login")
    @Size(max = 255, message = "登录路径长度不能超过 255 个字符")
    private String loginPath;

    @Schema(description = "登录方法", example = "POST")
    @Size(max = 16, message = "登录方法长度不能超过 16 个字符")
    private String loginMethod;

    @Schema(description = "登录用户名", example = "admin")
    @Size(max = 128, message = "登录用户名长度不能超过 128 个字符")
    private String loginUsername;

    @Schema(description = "登录密码。修改时留空表示不改", example = "secret")
    @Size(max = 255, message = "登录密码长度不能超过 255 个字符")
    private String loginPassword;

    @Schema(description = "用户名字段名", example = "username")
    @Size(max = 64, message = "用户名字段名长度不能超过 64 个字符")
    private String loginUsernameKey;

    @Schema(description = "密码字段名", example = "password")
    @Size(max = 64, message = "密码字段名长度不能超过 64 个字符")
    private String loginPasswordKey;

    @Schema(description = "token 的 JSON 路径", example = "data.accessToken")
    @Size(max = 128, message = "token 路径长度不能超过 128 个字符")
    private String tokenPath;

    @Schema(description = "token 请求头", example = "Authorization")
    @Size(max = 64, message = "token 请求头长度不能超过 64 个字符")
    private String tokenHeader;

    @Schema(description = "token 前缀", example = "Bearer")
    @Size(max = 32, message = "token 前缀长度不能超过 32 个字符")
    private String tokenPrefix;

    @Schema(description = "token 缓存秒数", example = "1800")
    private Integer tokenTtlSec;

    @Schema(description = "MQTT 主题前缀", example = "device/DEV-001/")
    @Size(max = 128, message = "MQTT 主题前缀长度不能超过 128 个字符")
    private String mqttTopicPrefix;

    @Schema(description = "仪器能否主动推送结果", example = "false")
    private Boolean callbackEnabled;

    @Schema(description = "默认轮询间隔(秒)", example = "5")
    private Integer pollIntervalSec;

    @Schema(description = "忙闲校验策略: AUTO 按动作决定/ALWAYS 强制预检/NEVER 从不预检(仪器不报空闲状态时用)", example = "AUTO")
    @Size(max = 32, message = "忙闲校验策略长度不能超过 32 个字符")
    private String busyCheckPolicy;

    @Schema(description = "并发策略: EXCLUSIVE 平台独占/DEVICE_QUEUED 仪器本地排队/PLATFORM_QUEUED 平台排队", example = "EXCLUSIVE")
    @Size(max = 32, message = "并发策略长度不能超过 32 个字符")
    private String concurrencyPolicy;

    @Schema(description = "允许同时在途命令数(EXCLUSIVE 恒为 1)", example = "1")
    @Min(value = 1, message = "同时在途命令数至少为 1")
    private Integer maxInflight;

    @Schema(description = "能力来源: DEVICE_DECLARED 驱动上报/MANUAL 手工", example = "MANUAL")
    private String capabilitySource;

    @Schema(description = "是否仿真模式", example = "true")
    private Boolean simulationMode;

    @Schema(description = "发现方式", example = "MANUAL")
    @Size(max = 32, message = "发现方式长度不能超过 32 个字符")
    private String discoveryType;

    @Schema(description = "备注", example = "一楼实验室读板机")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}

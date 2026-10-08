package cn.boheng.frame.module.device.dal.dataobject.device;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 设备台账 DO
 *
 * 对应表 device_info，继承 TenantBaseDO，由租户拦截器自动隔离。
 *
 * ⚠️ 表里有生成列 device_code_key（唯一键用），此处不映射 —— 由 DB 自动生成，MyBatis 不 insert 它。
 *
 * @author yinan
 */
@TableName("device_info")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfoDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备编码（业务唯一，跨模块寻址只认编码）
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备图片地址（可空，前端展示默认图）
     */
    private String imageUrl;

    /**
     * 设备类型编码（行为建模，不按型号；如 PLATE_READER）
     */
    private String deviceTypeCode;

    /**
     * 型号（如 Infinite 200 PRO）
     */
    private String model;

    /**
     * 厂商（如 Tecan）
     */
    private String vendor;

    /**
     * 序列号
     */
    private String serialNo;

    /**
     * 驱动类型（决定走哪个 DeviceDriver；如 TECAN_READER_NETWORK / SIMULATED）
     */
    private String driverType;

    /**
     * 在线状态: ONLINE 在线/OFFLINE 离线/MAINTENANCE 维护中/FAULT 故障
     */
    private String status;

    /**
     * 接入方式: HTTP 直连/MQTT 直连/NODE_RED 经Node-RED/SIMULATED 仿真
     */
    private String connectionType;

    /**
     * 接入地址(HTTP=base url；MQTT=broker host:port；NODE_RED=Node-RED http 入站节点 url)
     */
    private String endpointUrl;

    /**
     * MQTT 主题前缀(如 device/{code}/)，直连 MQTT 时使用
     */
    private String mqttTopicPrefix;

    /**
     * 仪器能否主动推送结果(决定 result_mode 用 CALLBACK 还是 POLL)
     */
    private Boolean callbackEnabled;

    /**
     * 默认轮询间隔(秒)，POLL 方式与遥测批量刷新共用
     */
    private Integer pollIntervalSec;

    /**
     * ★ 忙闲校验总开关: AUTO 按动作 need_busy_check 决定 / ALWAYS 下发前强制预检 / NEVER 从不预检
     *
     * ⚠️ 必须可配：不是所有仪器都返回空闲状态，写死会导致这类设备永远下不了命令。
     */
    private String busyCheckPolicy;

    /**
     * ★ 并发策略: EXCLUSIVE 平台独占 / DEVICE_QUEUED 仪器自带本地队列 / PLATFORM_QUEUED 平台侧排队
     *
     * ⚠️ 必须可配：部分仪器自带本地队列，强制独占会白白浪费仪器吞吐。
     */
    private String concurrencyPolicy;

    /**
     * 允许同时在途命令数(EXCLUSIVE 恒为 1；DEVICE_QUEUED 取仪器本地队列深度)
     */
    private Integer maxInflight;

    /**
     * 当前在途命令数(运行态；与 maxInflight 比较决定能否继续下发)
     */
    private Integer inflightCount;

    /**
     * 能力来源: DEVICE_DECLARED 驱动上报/MANUAL 手工(可覆盖)
     */
    private String capabilitySource;

    /**
     * 属性当前值快照(列表页与按状态选设备用；详细历史二期进 device_status_log)
     */
    private String telemetryJson;

    /**
     * 是否仿真模式(驱动按此分派到 SimulatedDriver)
     */
    private Boolean simulationMode;

    /**
     * 发现方式(一期手工录设备，字段预留 mDNS/Zeroconf 等)
     */
    private String discoveryType;

    /**
     * 当前占用命令编号(指向 device_command.id，用于 CAS 抢占判断忙闲)
     */
    private Long currentCommandId;

    /**
     * 锁持有方(command_no / USER:1 / TASK:xxx)
     */
    private String lockHolder;

    /**
     * 锁类型: COMMAND 命令占用/MANUAL 人工/MAINTENANCE 维护锁
     */
    private String lockType;

    /**
     * 锁获取时间
     */
    private LocalDateTime lockAcquiredTime;

    /**
     * 锁过期时间(锁必须能过期，否则设备永久锁死)
     */
    private LocalDateTime lockExpireTime;

    /**
     * 加锁原因(人工/维护加锁时必填)
     */
    private String lockReason;

    /**
     * 备注
     */
    private String remark;

}

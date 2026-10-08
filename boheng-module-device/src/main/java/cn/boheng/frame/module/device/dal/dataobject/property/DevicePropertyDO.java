package cn.boheng.frame.module.device.dal.dataobject.property;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 设备可观测属性定义 DO（遥测）
 *
 * 对应表 device_property。定义「这台设备能被观测什么」
 * （温度 / 仓门状态 / 耗材余量 ...），当前值快照落在 device_info.telemetry_json，
 * 详细历史二期进 device_status_log。
 *
 * ⚠️ 表里有生成列 property_code_key（= device_type_code#property_code，唯一键用），此处不映射。
 *
 * @author yinan
 */
@TableName("device_property")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePropertyDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备类型编码
     */
    private String deviceTypeCode;

    /**
     * 属性编码（如 TEMPERATURE / DOOR_STATE / CONSUMABLE_REMAIN）
     */
    private String propertyCode;

    /**
     * 属性名称（如 温度 / 仓门状态 / 耗材余量）
     */
    private String propertyName;

    /**
     * 数据类型: STRING/INTEGER/DECIMAL/BOOLEAN/ENUM/JSON
     */
    private String dataType;

    /**
     * 单位（如 ℃ / %）
     */
    private String unit;

    /**
     * 是否可读
     */
    private Boolean readable;

    /**
     * 是否可订阅（仪器支持推送时置 true）
     */
    private Boolean subscribable;

    /**
     * 轮询间隔（秒）；不订阅的属性按此批量刷新当前值
     */
    private Integer pollIntervalSec;

    /**
     * 能力来源: DEVICE_DECLARED 驱动上报 / MANUAL 手工（可覆盖）
     */
    private String source;

    /**
     * 启用状态: 0 启用 / 1 停用
     */
    private Integer status;

}

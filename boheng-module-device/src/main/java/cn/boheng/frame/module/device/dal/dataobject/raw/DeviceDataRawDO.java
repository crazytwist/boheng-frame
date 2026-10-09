package cn.boheng.frame.module.device.dal.dataobject.raw;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;
/**
 * 一次调用的原始响应，解析失败也保留
 */

@TableName("device_data_raw")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDataRawDO extends TenantBaseDO {
    /**
     * 主键，雪花算法
     */

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联命令编号
     */
    private Long commandId;

    /**
     * 设备编号
     */
    private Long deviceId;

    /**
     * 设备编码
     */
    private String deviceCode;

    /**
     * 原始数据类型。HTTP 调用记为 HTTP_RESPONSE
     */
    private String dataType;

    /**
     * 原始响应。非 JSON 正文包在 body 字段里
     */
    private String rawJson;

    /**
     * 响应 Content-Type
     */
    private String mimeType;

    /**
     * 结果格式：JSON、XML、CSV 或 TEXT
     */
    private String format;

    /**
     * 收到响应的时间
     */
    private LocalDateTime receivedTime;

}

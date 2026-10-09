package cn.boheng.frame.module.device.dal.dataobject.measurement;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;
/**
 * 一次测量。由成功调用的解析结果写入
 */

@TableName("device_measurement")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMeasurementDO extends TenantBaseDO {
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
     * 关联的原始响应编号
     */
    private Long dataRawId;

    /**
     * 设备编号
     */
    private Long deviceId;

    /**
     * 设备编码
     */
    private String deviceCode;

    /**
     * 检测模式，来自解析结果的 measureMode 或 mode
     */
    private String measureMode;

    /**
     * 波长，纳米
     */
    private Integer wavelengthNm;

    /**
     * 方法或脚本名
     */
    private String scriptName;

    /**
     * 这次调用的参数快照
     */
    private String methodParamsJson;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 操作者类型。人工测试记为 USER
     */
    private String operatorType;

    /**
     * 读数写入时间
     */
    private LocalDateTime readTime;

}

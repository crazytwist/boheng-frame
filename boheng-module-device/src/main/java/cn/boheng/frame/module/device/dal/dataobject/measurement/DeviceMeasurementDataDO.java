package cn.boheng.frame.module.device.dal.dataobject.measurement;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
/**
 * 一个孔的读数
 */

@TableName("device_measurement_data")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMeasurementDataDO extends TenantBaseDO {
    /**
     * 主键，雪花算法
     */

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联测量编号
     */
    private Long measurementId;

    /**
     * 孔位，如 A1
     */
    private String wellPosition;

    /**
     * 行号，从 1 起。A 是 1
     */
    private Integer rowIndex;

    /**
     * 列号，从 1 起
     */
    private Integer colIndex;

    /**
     * 波长，纳米
     */
    private Integer wavelengthNm;

    /**
     * 读数值
     */
    private BigDecimal readValue;

    /**
     * 单位
     */
    private String unit;

    /**
     * 解析前的原始文本
     */
    private String rawText;

    /**
     * 质量标记，如 OVER_RANGE
     */
    private String qualityFlag;

}

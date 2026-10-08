package cn.boheng.frame.module.device.dal.dataobject.measurement;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("device_measurement_data")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMeasurementDataDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long measurementId;

    private String wellPosition;

    private Integer rowIndex;

    private Integer colIndex;

    private Integer wavelengthNm;

    private BigDecimal readValue;

    private String unit;

    private String rawText;

    private String qualityFlag;

}

package cn.boheng.frame.module.device.dal.dataobject.measurement;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("device_measurement")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceMeasurementDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long commandId;

    private Long dataRawId;

    private Long deviceId;

    private String deviceCode;

    private String measureMode;

    private Integer wavelengthNm;

    private String scriptName;

    private String methodParamsJson;

    private String operator;

    private String operatorType;

    private LocalDateTime readTime;

}

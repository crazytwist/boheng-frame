package cn.boheng.frame.module.device.dal.dataobject.raw;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("device_data_raw")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDataRawDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long commandId;

    private Long deviceId;

    private String deviceCode;

    private String dataType;

    private String rawJson;

    private String mimeType;

    private String format;

    private LocalDateTime receivedTime;

}

package cn.boheng.frame.module.device.dal.dataobject.command;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 一次设备调用的事实。业务侧只追加，不提供删除。
 */
@TableName("device_command")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String commandNo;

    private String sourceType;

    private Integer attempt;

    private Long deviceId;

    private String deviceCode;

    private String deviceTypeCode;

    private String actionCode;

    private String dispatchMode;

    private String resultMode;

    private Integer pollCount;

    private String waitingFor;

    private String status;

    private String paramsJson;

    private String payloadJson;

    private String requestJson;

    private String responseJson;

    private String errorMsg;

    private String codecCode;

    private Long dataRawId;

    private String operator;

    private String operatorType;

    private LocalDateTime acquiredAt;

    private LocalDateTime sentAt;

    private LocalDateTime finishedAt;

    private Integer version;

}

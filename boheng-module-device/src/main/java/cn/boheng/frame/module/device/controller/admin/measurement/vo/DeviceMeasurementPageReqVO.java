package cn.boheng.frame.module.device.controller.admin.measurement.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceMeasurementPageReqVO extends PageParam {

    private String deviceCode;
    private Long commandId;

}

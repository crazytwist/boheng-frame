package cn.boheng.frame.module.device.controller.admin.measurement.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
/**
 * 测量记录分页条件
 */

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceMeasurementPageReqVO extends PageParam {

    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 命令编号
     */
    private Long commandId;

}

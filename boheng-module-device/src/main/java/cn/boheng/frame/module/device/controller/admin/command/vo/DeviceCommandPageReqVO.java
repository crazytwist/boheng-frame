package cn.boheng.frame.module.device.controller.admin.command.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 命令记录分页")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceCommandPageReqVO extends PageParam {

    /**
     * 命令号
     */
    private String commandNo;
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 动作编码
     */
    private String actionCode;
    /**
     * 命令状态
     */
    private String status;

}

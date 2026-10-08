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

    private String commandNo;
    private String deviceCode;
    private String actionCode;
    private String status;

}

package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 设备任务分页条件。给管理后台监控队列用。
 */
@Schema(description = "管理后台 - 设备任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowDeviceTaskPageReqVO extends PageParam {

    @Schema(description = "所属运行编号", example = "1")
    private Long executionId;

    @Schema(description = "状态：PENDING / CLAIMED / DONE / FAILED / CANCELLED", example = "PENDING")
    private String status;

    @Schema(description = "目标设备编码", example = "TECAN-01")
    private String deviceCode;

    @Schema(description = "任务类型 / 动作编码", example = "read-plate")
    private String actionCode;

}

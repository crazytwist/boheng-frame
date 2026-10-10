package cn.boheng.frame.module.flow.controller.admin.step.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返回给步骤列表的一行。
 */
@Schema(description = "管理后台 - 实验步骤 Response VO")
@Data
public class FlowStepRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "步骤编码")
    private String stepKey;

    @Schema(description = "步骤名称")
    private String name;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "等待秒数")
    private Integer timeoutSec;

    @Schema(description = "尝试次数")
    private Integer maxAttempts;

    @Schema(description = "说明")
    private String remark;

    @Schema(description = "状态：0 启用，1 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}

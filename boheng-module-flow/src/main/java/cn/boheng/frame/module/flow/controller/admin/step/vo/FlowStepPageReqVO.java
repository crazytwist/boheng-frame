package cn.boheng.frame.module.flow.controller.admin.step.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 步骤分页条件。
 */
@Schema(description = "管理后台 - 实验步骤分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowStepPageReqVO extends PageParam {

    @Schema(description = "步骤编码", example = "spin")
    private String stepKey;

    @Schema(description = "步骤名称", example = "离心")
    private String name;

    @Schema(description = "设备编码", example = "DEV-001")
    private String deviceCode;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

}

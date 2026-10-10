package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 节点执行分页条件。一次运行的时间线从这里查。
 */
@Schema(description = "管理后台 - 流程节点执行分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowStepExecutionPageReqVO extends PageParam {

    @Schema(description = "运行编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long executionId;

    @Schema(description = "节点编码", example = "read_plate")
    private String nodeId;

    @Schema(description = "状态：PENDING / RUNNING / WAITING / SUCCESS / FAILED / DEAD / SKIPPED / CANCELLED")
    private String status;

}

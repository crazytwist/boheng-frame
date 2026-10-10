package cn.boheng.frame.module.flow.controller.admin.execution.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 运行分页条件。
 */
@Schema(description = "管理后台 - 流程运行分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowExecutionPageReqVO extends PageParam {

    @Schema(description = "运行业务号", example = "exe_20261010_0001")
    private String executionNo;

    @Schema(description = "流程编码", example = "elisa-assay")
    private String pipelineKey;

    @Schema(description = "状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED", example = "RUNNING")
    private String status;

    @Schema(description = "业务关联键", example = "EXP-20261010-001")
    private String businessKey;

}

package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 流程版本分页条件。
 */
@Schema(description = "管理后台 - 流程定义分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowDefinitionPageReqVO extends PageParam {

    @Schema(description = "流程编码", example = "plate_read")
    private String pipelineKey;

    @Schema(description = "流程名称", example = "读板")
    private String name;

    @Schema(description = "状态：DRAFT / PUBLISHED / RETIRED", example = "DRAFT")
    private String status;

}

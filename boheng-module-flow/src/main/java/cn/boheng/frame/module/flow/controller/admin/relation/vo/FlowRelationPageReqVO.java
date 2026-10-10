package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 流程步骤关联的分页条件。
 */
@Schema(description = "管理后台 - 流程步骤分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowRelationPageReqVO extends PageParam {

    @Schema(description = "流程版本编号")
    private Long definitionId;

}

package cn.boheng.frame.module.flow.controller.admin.error.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 流程错误记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowErrorRecordPageReqVO extends PageParam {

    @Schema(description = "错误业务号")
    private String errorNo;

    @Schema(description = "运行编号")
    private Long executionId;

    @Schema(description = "节点编码")
    private String nodeId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "来源")
    private String sourceType;
}

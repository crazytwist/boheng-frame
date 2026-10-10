package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程版本。列表不带节点，详情带节点。
 */
@Schema(description = "管理后台 - 流程定义 Response VO")
@Data
public class FlowDefinitionRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "流程编码")
    private String pipelineKey;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "说明")
    private String remark;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "发布指纹")
    private String checksum;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "步骤数。列表使用")
    private Integer nodeCount;

    @Schema(description = "步骤。详情使用")
    private List<FlowNodeRespVO> nodes;

}

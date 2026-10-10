package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布前检查出的一处问题。编排页用 nodeId 定位到步骤。
 */
@Schema(description = "管理后台 - 流程校验问题 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowIssueRespVO {

    @Schema(description = "步骤编码。整条流程的问题为空")
    private String nodeId;

    @Schema(description = "原因")
    private String message;

}

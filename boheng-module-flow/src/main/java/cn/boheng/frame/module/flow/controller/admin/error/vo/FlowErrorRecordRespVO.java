package cn.boheng.frame.module.flow.controller.admin.error.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 流程错误记录 Response VO")
@Data
public class FlowErrorRecordRespVO {

    private Long id;
    private String errorNo;
    private String dedupKey;
    private String sourceType;
    private String sourceRefId;
    private Long executionId;
    private String nodeId;
    private Integer iterationNo;
    private Integer attempt;
    private String errorCode;
    private String category;
    private String severity;
    private String certainty;
    private String retryMode;
    private String message;
    private String detailJson;
    private String status;
    private String resolution;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;

    @Schema(description = "处置流水（详情接口返回）")
    private List<FlowErrorActionRespVO> actions;
}

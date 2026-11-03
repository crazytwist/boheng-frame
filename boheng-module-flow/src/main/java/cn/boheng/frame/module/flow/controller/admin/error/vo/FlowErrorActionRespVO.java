package cn.boheng.frame.module.flow.controller.admin.error.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 流程错误处置流水 Response VO")
@Data
public class FlowErrorActionRespVO {

    private Long id;
    private Long errorId;
    private String action;
    private String operator;
    private String actionResult;
    private String comment;
    private LocalDateTime createTime;
}

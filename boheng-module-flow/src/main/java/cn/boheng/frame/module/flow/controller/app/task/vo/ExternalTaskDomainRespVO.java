package cn.boheng.frame.module.flow.controller.app.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 清华 {@code ExternalTaskDomain} 对齐结构（设备拉取可执行任务）。
 */
@Data
@Schema(description = "外部可执行任务")
public class ExternalTaskDomainRespVO {

    @Schema(description = "任务 id")
    private Integer taskId;

    @Schema(description = "所属工单")
    private Integer batchId;

    @Schema(description = "任务类型（设备侧用流程节点序号标识，非清华 ST_* 枚举）")
    private Integer taskType;

    @Schema(description = "任务名称（当前为流程节点序号字符串，设备拉取任务的唯一步骤标识）")
    private String name;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "任务配置")
    private Object config;

    @Schema(description = "目标区域")
    private List<Destination> destinationList;

    @Schema(description = "实验室器具")
    private List<Labware> labwareList;

    @Data
    @Schema(description = "目标区域")
    public static class Destination {

        @Schema(description = "目标区域编码")
        private Integer destination;

        @Schema(description = "实验室器具 id")
        private List<Integer> labwareIdList;
    }

    @Data
    @Schema(description = "实验室器具")
    public static class Labware {

        @Schema(description = "实验室器具 id")
        private Integer id;

        @Schema(description = "子实验室器具 id")
        private List<Integer> childLabwareList;
    }

}

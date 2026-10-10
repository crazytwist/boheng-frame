package cn.boheng.frame.module.flow.controller.admin.definition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑弹窗提交的一步。设备和动作允许先空着，发布时才强制。
 */
@Schema(description = "管理后台 - 流程节点保存 Request VO")
@Data
public class FlowNodeSaveReqVO {

    @Schema(description = "步骤编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "spin")
    @NotEmpty(message = "步骤编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$", message = "步骤编码须是小写字母开头，只含小写、数字和下划线")
    private String nodeId;

    @Schema(description = "步骤名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "离心")
    @NotEmpty(message = "步骤名称不能为空")
    @Size(max = 128, message = "步骤名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "设备编码", example = "DEV-001")
    @Size(max = 64, message = "设备编码长度不能超过 64 个字符")
    private String deviceCode;

    @Schema(description = "动作编码", example = "READ_PLATE")
    @Size(max = 64, message = "动作编码长度不能超过 64 个字符")
    private String actionCode;

    @Schema(description = "等待秒数", example = "300")
    @Min(value = 1, message = "等待时间至少 1 秒")
    @Max(value = 86400, message = "等待时间不能超过 24 小时")
    private Integer timeoutSec;

    @Schema(description = "尝试次数，含首次", example = "1")
    @Min(value = 1, message = "尝试次数至少 1")
    @Max(value = 5, message = "尝试次数不能超过 5")
    private Integer maxAttempts;

    @Schema(description = "参数映射")
    @Valid
    private List<FlowMappingReqVO> mappings = new ArrayList<>();

}

package cn.boheng.frame.module.flow.controller.admin.step.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建或修改一条步骤。编码只在新建时生效。
 * 步骤类型 + 类型配置决定这一步「怎么执行」，见 engine/type/builtin。
 */
@Schema(description = "管理后台 - 实验步骤保存 Request VO")
@Data
public class FlowStepSaveReqVO {

    @Schema(description = "编号。新建时为空")
    private Long id;

    @Schema(description = "步骤编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "spin")
    @NotEmpty(message = "步骤编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,31}$", message = "步骤编码须是小写字母开头，只含小写、数字和下划线")
    private String stepKey;

    @Schema(description = "步骤名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "离心")
    @NotEmpty(message = "步骤名称不能为空")
    @Size(max = 128, message = "步骤名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "步骤类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "device.command")
    @NotEmpty(message = "步骤类型不能为空")
    @Size(max = 64, message = "步骤类型长度不能超过 64 个字符")
    private String stepType;

    @Schema(description = "类型配置 JSON，结构由步骤类型决定", example = "{\"deviceCode\":\"DEV-001\",\"actionCode\":\"READ_PLATE\"}")
    @Size(max = 4096, message = "类型配置长度不能超过 4096 个字符")
    private String typeConfig;

    @Schema(description = "默认输入 JSON。节点引用时可覆盖", example = "{\"speed\":3000}")
    @Size(max = 4096, message = "默认输入长度不能超过 4096 个字符")
    private String defaultParams;

    @Schema(description = "等待秒数", example = "300")
    @NotNull(message = "等待时间不能为空")
    @Min(value = 1, message = "等待时间至少 1 秒")
    @Max(value = 86400, message = "等待时间不能超过 24 小时")
    private Integer timeoutSec;

    @Schema(description = "尝试次数，含首次", example = "1")
    @NotNull(message = "尝试次数不能为空")
    @Min(value = 1, message = "尝试次数至少 1")
    @Max(value = 5, message = "尝试次数不能超过 5")
    private Integer maxAttempts;

    @Schema(description = "说明")
    @Size(max = 512, message = "说明长度不能超过 512 个字符")
    private String remark;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

}

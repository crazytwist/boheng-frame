package cn.boheng.frame.module.device.controller.admin.command.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工结束一条卡住的设备命令。
 *
 * <p>用于回调丢失、设备面板其实已经做完这类情况：平台等不到 7.1.3，命令会一直停在
 * WAITING 直到 Sweeper 判超时。运维据现场事实给定结论，比干等 5 分钟更贴近实际。</p>
 *
 * <p>注意这是「人工给结论」，不是「人工补投回调」——回调令牌只存了摘要，
 * 补投一条回调必然被 403 拒掉。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 人工结束设备命令")
@Data
public class DeviceCommandCloseReqVO {

    @Schema(description = "命令主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "命令主键不能为空")
    private Long id;

    /**
     * 只允许 SUCCEEDED / FAILED：TIMED_OUT 由 Sweeper 负责，
     * CANCELLED 语义是「没发出去」，都不该由人工在这里产生。
     */
    @Schema(description = "结束成什么状态：SUCCEEDED / FAILED", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "FAILED")
    @NotEmpty(message = "结束状态不能为空")
    private String status;

    /**
     * 故意设成必填。留空在流程侧等价于「可重试」，会让一段没人确认过幂等性的操作被重跑一遍。
     */
    @Schema(description = "这次结果是否值得重试。必须明确给出，留空会被流程当成可重试",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "必须明确是否可重试")
    private Boolean retryable;

    /**
     * 失败时用；不填按 BUSINESS 记。成功时忽略。
     */
    @Schema(description = "失败分类：TRANSPORT / DEVICE_REJECTED / BUSINESS / TIMEOUT；失败时默认 BUSINESS")
    @Size(max = 32, message = "失败分类长度不能超过 32 个字符")
    private String errorType;

    @Schema(description = "人工结束的原因，会写进命令备注", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "设备面板已显示完成，平台未收到回调")
    @NotEmpty(message = "请填写人工结束的原因")
    @Size(max = 150, message = "原因不能超过 150 个字符")
    private String remark;

}

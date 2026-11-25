package cn.boheng.frame.module.flow.controller.app.callback.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 外部业务系统回报一次节点结果。
 *
 * <p>与设备回报的差别：这里认的是「哪次执行哪一步」，不需要知道命令号；
 * 令牌由平台按执行/节点/尝试派生，调用方从流程上下文里拿。</p>
 */
@Schema(description = "App - 流程外部回调 Request VO")
@Data
public class ExternalCallbackReqVO {

    @Schema(description = "true 成功，false 失败", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "执行结果不能为空")
    private Boolean success;

    @Schema(description = "成功结果 JSON，写进节点输出供下游参数映射取用")
    @Size(max = 4096, message = "结果 JSON 长度不能超过 4096 个字符")
    private String resultJson;

    @Schema(description = "失败说明")
    @Size(max = 512, message = "失败说明长度不能超过 512 个字符")
    private String errorMessage;

    @Schema(description = "第几次尝试。不填则用等待行上的值，仅用于挡住迟到的旧回调")
    private Integer attempt;

    @Schema(description = "回调令牌。也可放在 token 参数或 X-Callback-Token 请求头")
    @Size(max = 128, message = "令牌长度不能超过 128 个字符")
    private String token;

}

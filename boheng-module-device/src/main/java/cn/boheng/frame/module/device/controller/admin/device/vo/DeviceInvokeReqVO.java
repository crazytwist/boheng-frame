package cn.boheng.frame.module.device.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 向一台设备的某个动作发 HTTP。配置了登录路径时，先登录并带上缓存的 token。
 */
@Schema(description = "管理后台 - 调用设备 HTTP 动作")
@Data
public class DeviceInvokeReqVO {

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备编号不能为空")
    private Long deviceId;

    @Schema(description = "动作编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "READ_PLATE")
    @NotEmpty(message = "动作编码不能为空")
    private String actionCode;

    @Schema(description = "参数 JSON，对应动作的请求模板占位符")
    private String paramsJson;

    @Schema(description = "解析规则编码。空则用动作上配置的规则")
    private String codecCode;

}

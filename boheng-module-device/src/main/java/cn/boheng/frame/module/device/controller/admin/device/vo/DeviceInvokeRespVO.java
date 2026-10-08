package cn.boheng.frame.module.device.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 设备 HTTP 调用结果")
@Data
public class DeviceInvokeRespVO {

    @Schema(description = "HTTP 状态码")
    private Integer status;

    @Schema(description = "响应体")
    private String body;

    @Schema(description = "响应 Content-Type")
    private String contentType;

    @Schema(description = "本次是否因 token 失效重新登录过")
    private Boolean retriedLogin;

    @Schema(description = "命令编号")
    private Long commandId;

    @Schema(description = "命令号")
    private String commandNo;

    @Schema(description = "实际请求方法")
    private String httpMethod;

    @Schema(description = "实际请求地址")
    private String requestUrl;

    @Schema(description = "实际请求体")
    private String requestBody;

}

package cn.boheng.frame.module.device.controller.admin.command.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceCommandRespVO {

    private Long id;
    private String commandNo;
    private String sourceType;
    private Long deviceId;
    private String deviceCode;
    private String deviceTypeCode;
    private String actionCode;
    private String dispatchMode;
    private String resultMode;
    private String status;
    private String paramsJson;
    private String payloadJson;
    private String requestJson;
    private String responseJson;
    private String errorMsg;
    private String codecCode;
    private Long dataRawId;
    private String operator;
    private LocalDateTime acquiredAt;
    private LocalDateTime sentAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;

    @Schema(description = "原始响应正文")
    private String rawBody;

    @Schema(description = "原始响应格式")
    private String rawFormat;

}

package cn.boheng.frame.module.device.controller.admin.codec.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 试解析")
@Data
public class DeviceCodecPreviewReqVO {

    @NotEmpty(message = "解析类型不能为空")
    private String parseType;

    private String fieldMapping;

    private String regexPattern;

    @NotEmpty(message = "样例报文不能为空")
    private String sampleRaw;

}

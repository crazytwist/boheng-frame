package cn.boheng.frame.module.device.controller.admin.codec.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 试解析")
@Data
public class DeviceCodecPreviewReqVO {
    /**
     * 解析类型：JSON 或 REGEX
     */

    @NotEmpty(message = "解析类型不能为空")
    private String parseType;

    /**
     * 字段映射 JSON
     */
    private String fieldMapping;

    /**
     * 正则
     */
    private String regexPattern;
    /**
     * 样例原文
     */

    @NotEmpty(message = "样例报文不能为空")
    private String sampleRaw;

}

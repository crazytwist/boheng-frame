package cn.boheng.frame.module.device.controller.admin.codec.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 解析规则保存")
@Data
public class DeviceCodecSaveReqVO {

    private Long id;

    @NotEmpty(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64 个字符")
    private String codecCode;

    @NotEmpty(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128 个字符")
    private String codecName;

    @NotEmpty(message = "解析类型不能为空")
    @Size(max = 32, message = "解析类型长度不能超过 32 个字符")
    private String parseType;

    private String fieldMapping;

    @Size(max = 1024, message = "正则长度不能超过 1024 个字符")
    private String regexPattern;

    private String sampleRaw;

    private Integer status;

    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}

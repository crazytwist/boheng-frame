package cn.boheng.frame.module.device.controller.admin.codec.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 解析规则")
@Data
public class DeviceCodecRespVO {

    private Long id;
    private String codecCode;
    private String codecName;
    private String parseType;
    private String fieldMapping;
    private String regexPattern;
    private String sampleRaw;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;

}

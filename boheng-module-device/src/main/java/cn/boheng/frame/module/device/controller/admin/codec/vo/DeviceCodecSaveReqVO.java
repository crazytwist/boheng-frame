package cn.boheng.frame.module.device.controller.admin.codec.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 解析规则保存")
@Data
public class DeviceCodecSaveReqVO {

    /**
     * 主键，雪花算法
     */
    private Long id;
    /**
     * 规则编码
     */

    @NotEmpty(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64 个字符")
    private String codecCode;
    /**
     * 规则名称
     */

    @NotEmpty(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128 个字符")
    private String codecName;
    /**
     * 解析类型：JSON 或 REGEX
     */

    @NotEmpty(message = "解析类型不能为空")
    @Size(max = 32, message = "解析类型长度不能超过 32 个字符")
    private String parseType;

    /**
     * 字段映射 JSON
     */
    private String fieldMapping;
    /**
     * 正则。解析类型为 REGEX 时使用
     */

    @Size(max = 1024, message = "正则长度不能超过 1024 个字符")
    private String regexPattern;

    /**
     * 样例原文，供页面上试解析
     */
    private String sampleRaw;

    /**
     * 启用状态：0 启用，1 停用
     */
    private Integer status;
    /**
     * 备注
     */

    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}

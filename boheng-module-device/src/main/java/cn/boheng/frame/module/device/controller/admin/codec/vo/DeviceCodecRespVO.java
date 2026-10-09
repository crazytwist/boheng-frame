package cn.boheng.frame.module.device.controller.admin.codec.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 解析规则")
@Data
public class DeviceCodecRespVO {

    /**
     * 主键，雪花算法
     */
    private Long id;
    /**
     * 解析规则编码
     */
    private String codecCode;
    /**
     * 解析规则名称
     */
    private String codecName;
    private String parseType;
    private String fieldMapping;
    /**
     * 正则。解析类型为 REGEX 时使用
     */
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
    private String remark;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}

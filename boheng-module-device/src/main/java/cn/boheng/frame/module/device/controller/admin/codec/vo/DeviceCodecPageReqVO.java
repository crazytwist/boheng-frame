package cn.boheng.frame.module.device.controller.admin.codec.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 解析规则分页")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeviceCodecPageReqVO extends PageParam {

    @Schema(description = "规则编码")
    private String codecCode;

    @Schema(description = "规则名称")
    private String codecName;

    @Schema(description = "解析类型 JSON/REGEX")
    private String parseType;

    @Schema(description = "启用状态")
    private Integer status;

}

package cn.boheng.frame.module.transport.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/**
 * 创建运输需求的跨模块请求参数。
 */
@Data
public class TransportRequestCreateReqDTO {
    /** 来源系统。 */
    @NotBlank private String sourceType;
    /** 来源业务引用。 */
    @NotBlank private String sourceRef;
    /** 运输对象列表。 */
    @NotEmpty private List<TransportItemReqDTO> items;
    /** 可选优先级，数值越大越优先。 */
    private Integer priority;
    /** 防重复提交键。 */
    @NotBlank private String idempotencyKey;
}

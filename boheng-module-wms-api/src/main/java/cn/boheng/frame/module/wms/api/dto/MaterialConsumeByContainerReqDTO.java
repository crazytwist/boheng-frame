package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 按容器消耗请求（契约层）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialConsumeByContainerReqDTO extends MaterialOperationTraceDTO {

    /** 容器实例编号（父实例） */
    private Long rootInstanceId;

    /** 领取数量 */
    private Integer count;

    /** 挑选策略：POSITION（默认）/ ID */
    private String strategy;

    /** 消耗后子实例状态；可空=USED */
    private String afterStatus;

    /** 是否允许挑 RESERVED；默认 false */
    private Boolean includeReserved;

}

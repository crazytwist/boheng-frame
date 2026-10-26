package cn.boheng.frame.module.transport.api.dto;

import lombok.Data;

/**
 * 创建运输需求后的最小返回结果。
 */
@Data
public class TransportRequestRespDTO {
    /** 运输需求 ID。 */
    private Long requestId;
    /** 对外运输需求编号。 */
    private String requestNo;
    /** 当前需求状态。 */
    private String status;
}

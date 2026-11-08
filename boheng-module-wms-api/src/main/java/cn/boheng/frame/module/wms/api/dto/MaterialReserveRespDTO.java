package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 预留结果。 */
@Data
public class MaterialReserveRespDTO implements Serializable {

    /** 同批共享预留号 */
    private String reservationId;

    private List<Long> instanceIds;
    private List<Long> movementIds;
    private String operationId;
    private Boolean idempotentReplay;
}

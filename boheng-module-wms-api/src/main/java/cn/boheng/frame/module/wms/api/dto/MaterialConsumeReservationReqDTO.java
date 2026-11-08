package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/** 有所有权校验的消耗入口。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialConsumeReservationReqDTO extends MaterialOperationTraceDTO {

    private String reservationId;
    private String executionId;
    private List<MaterialConsumeItemDTO> items;
}

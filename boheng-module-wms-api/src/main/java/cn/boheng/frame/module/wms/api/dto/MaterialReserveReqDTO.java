package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/** 按执行预留一批实例（共用 reservationId）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialReserveReqDTO extends MaterialOperationTraceDTO {

    /** 预留所有者（Flow 执行编号） */
    private String executionId;

    /** 目标实例编号（去重后整批） */
    private List<Long> instanceIds;

    /** 到期时间（必填，须晚于当前） */
    private LocalDateTime reservedUntil;
}

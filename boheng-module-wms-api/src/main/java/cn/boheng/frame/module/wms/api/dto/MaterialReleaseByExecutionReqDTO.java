package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 按执行号批量释放仍处于 RESERVED 的预留。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialReleaseByExecutionReqDTO extends MaterialOperationTraceDTO {

    private String executionId;

    /** 释放原因（写入流水 remark） */
    private String reason;
}

package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物料写操作公共追溯字段（契约层）。
 *
 * <p>对应实现侧 {@code MovementTraceBaseReqVO}。Flow 调用时通常：
 * {@code bizSource=FLOW}，{@code idempotentKey} 由 {@code FlowTraceKeys} 生成。</p>
 */
@Data
public class MaterialOperationTraceDTO implements Serializable {

    /** 备注 / 操作原因 */
    private String remark;

    /** 业务来源：MANUAL/TASK/DEVICE/IMPORT/FLOW；不传默认 MANUAL */
    private String bizSource;

    /** 操作者类型：USER/DEVICE/AUTO；不传默认 USER */
    private String operatorType;

    /** 操作者（设备编码 / 用户 id / system） */
    private String operator;

    /** 关联业务类型 */
    private String refType;

    /** 关联业务编号 */
    private String refId;

    /** 关联业务单号 */
    private String refNo;

    /**
     * 调用方业务幂等键（勿自带 {@code #index}）。
     * WMS 流水行键 = {@code idempotentKey#index}。
     */
    private String idempotentKey;

    /** 业务操作时间；不传取当前时间 */
    private LocalDateTime operateTime;

    /** 操作批次号；不传由后端生成 */
    private String operationId;

}

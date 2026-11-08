package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 样本实物化：按容器类型创建 quantity 个挂 sample_id 的实例。
 *
 * <p>{@code quantity} 只表示实例个数，不隐式拆分体积。</p>
 */
@Data
public class SampleInstantiateReqDTO implements Serializable {

    /** 样本业务编号 */
    private String sampleId;

    /** 容器类型编码 */
    private String containerTypeCode;

    /** 创建实例个数（>=1） */
    private Integer quantity;

    /**
     * 每个实例初始体积（μL，可选）。
     * 不传则用样本 {@code initialVolUl}（若有）；仍空则不写体积。
     */
    private BigDecimal initialVolUl;

    /** 稳定幂等键（建议必填）；重试回放原实例，不重复建账 */
    private String idempotentKey;
}

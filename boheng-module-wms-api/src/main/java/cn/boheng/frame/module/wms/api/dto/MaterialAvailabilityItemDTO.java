package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 综合可用性检查条目。
 *
 * <p>{@code kind}: SAMPLE / CONTENT / CONTAINER / SLOT</p>
 */
@Data
public class MaterialAvailabilityItemDTO implements Serializable {

    /** SAMPLE / CONTENT / CONTAINER / SLOT */
    private String kind;

    /** 业务编码：sampleId / contentDefCode / containerTypeCode / slotType 等 */
    private String code;

    /** 样本编号（kind=SAMPLE 时与 code 二选一，优先 sampleId） */
    private String sampleId;

    /** 内容物编码（kind=CONTENT） */
    private String contentDefCode;

    /** 容器类型编码（kind=CONTAINER 或 SAMPLE 可选） */
    private String containerTypeCode;

    /** 需要的实例/槽位数量（默认 1） */
    private Integer requiredQty;

    /** 需要的总体积 μL（可选，按实例合计） */
    private BigDecimal requiredVolUl;

    /** 槽位区域编码（kind=SLOT） */
    private String zoneCode;

    /** 设备编码（kind=SLOT） */
    private String deviceCode;

    /** 槽位类型（kind=SLOT） */
    private String slotType;

    /** 槽位使用状态过滤，默认 FREE */
    private String slotStatus;
}

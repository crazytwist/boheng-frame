package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 衍生样本登记（单父；不伪装多父 MERGE）。
 *
 * <p>{@code sampleId} 建议必填以保证重试稳定；{@code parentSampleId}/{@code deriveType} 必填。</p>
 */
@Data
public class SampleDeriveRegisterReqDTO implements Serializable {

    /** 子样本业务编号（建议必填；不传则由 WMS 生成，但重试不稳定） */
    private String sampleId;

    /** 父样本编号（必填） */
    private String parentSampleId;

    /** 衍生类型：SPLIT/ALIQUOT/MERGE/ADD_REAGENT（不可为 ORIGINAL） */
    private String deriveType;

    private String sampleName;
    private String sampleType;
    private String contentDefCode;
    private String containerTypeCode;
    private BigDecimal initialVolUl;
    private String experimentParams;
    private String batchNo;
    private String orderNo;
    private Integer priority;
    private String source;
}

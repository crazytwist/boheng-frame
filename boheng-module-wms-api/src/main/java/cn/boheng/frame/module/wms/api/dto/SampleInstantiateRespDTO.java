package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 样本实物化结果。 */
@Data
public class SampleInstantiateRespDTO implements Serializable {

    private String sampleId;

    /** 有序实例编号 */
    private List<Long> instanceIds;

    /** 本批操作批次号 */
    private String operationId;

    /** true=同幂等键回放 */
    private Boolean idempotentReplay;
}

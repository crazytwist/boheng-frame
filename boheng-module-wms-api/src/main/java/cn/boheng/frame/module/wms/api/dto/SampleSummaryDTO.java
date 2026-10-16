package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 样本摘要 DTO（契约层）
 *
 * <p>「读样本」的结果：wms 返回给流程的最小样本信息，只带流程做
 * 「读实验参数 / 认样本状态」需要的字段，不带任何 DAL/实现细节。</p>
 *
 * @author yinan
 */
@Data
public class SampleSummaryDTO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 样本业务编号（跨模块寻址只认编码，不建外键）
     */
    private String sampleId;

    /**
     * 样本名称/描述
     */
    private String sampleName;

    /**
     * 样本类型
     */
    private String sampleType;

    /**
     * 样本状态（八态，见 SampleStatus）
     */
    private String status;

    /**
     * 内容物编码
     */
    private String contentDefCode;

    /**
     * 期望容器类型编码
     */
    private String containerTypeCode;

    /**
     * 初始体积（μL）
     */
    private BigDecimal initialVolUl;

    /**
     * 实验参数（JSON，流程步骤执行时读）
     */
    private String experimentParams;

    /**
     * 当前流程执行编号
     */
    private String currentExecutionId;

    /**
     * 当前所在步骤节点编号
     */
    private String currentNodeId;

    /**
     * 样本批次号
     */
    private String batchNo;

}

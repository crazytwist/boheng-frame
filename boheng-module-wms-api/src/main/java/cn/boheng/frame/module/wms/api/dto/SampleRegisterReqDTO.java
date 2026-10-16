package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 样本登记 DTO（契约层）
 *
 * <p>流程的「登记」节点经此把样本提前建档。样本是业务对象，可先于实物存在；
 * 实例化时再把它挂到 material_instance（sample_id）。</p>
 *
 * @author yinan
 */
@Data
public class SampleRegisterReqDTO implements Serializable {

    /**
     * 样本业务编号（条码/二维码，租户内唯一）。调用方不传时由 wms 生成。
     */
    private String sampleId;

    /**
     * 父样本编号（NULL=原始样本；分样/分装时指向母样本）
     */
    private String parentSampleId;

    /**
     * 衍生类型：ORIGINAL/SPLIT/ALIQUOT/MERGE/ADD_REAGENT。
     * 原始样本（parentSampleId 为空）可省略；衍生样本（parentSampleId 非空）必填且不能为 ORIGINAL。
     */
    private String deriveType;

    /**
     * 样本名称/描述
     */
    private String sampleName;

    /**
     * 样本类型（字典 wms_sample_type）: WATER/BLOOD/SOIL/...
     */
    private String sampleType;

    /**
     * 内容物编码（样本是什么物质，关联 wms_content_def）
     */
    private String contentDefCode;

    /**
     * 期望容器类型编码（关联 wms_container_type）
     */
    private String containerTypeCode;

    /**
     * 初始体积（μL，登记承诺量）
     */
    private BigDecimal initialVolUl;

    /**
     * 实验参数（JSON，流程步骤执行时读，如 {"targetPh":7.2,"volumeUl":500}）
     */
    private String experimentParams;

    /**
     * 样本批次号（同一批送检样本共享）
     */
    private String batchNo;

    /**
     * 检测单/委托单号
     */
    private String orderNo;

    /**
     * 优先级: 0 普通/1 加急/2 特急
     */
    private Integer priority;

    /**
     * 来源: MANUAL 手动/IMPORT 导入/LIS 系统对接/DEVICE 设备采集
     */
    private String source;

}

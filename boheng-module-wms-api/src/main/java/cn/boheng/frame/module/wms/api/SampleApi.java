package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.SampleRegisterReqDTO;
import cn.boheng.frame.module.wms.api.dto.SampleSummaryDTO;

import java.util.List;

/**
 * 样本域对外 API（契约层，提供给其它模块调用）
 *
 * <p>样本是「业务对象」，与实物（material_instance）分离。流程（flow）通过本契约
 * 完成「登记样本 → 读取实验参数 → 驱动状态流转」，不直接碰 wms 实现。</p>
 *
 * <p>实现位于 boheng-module-wms 的 {@code SampleApiImpl}，由 Spring 注入；
 * 消费方（flow 等）只依赖本接口 + DTO，绝不依赖实现模块。</p>
 *
 * @author yinan
 */
public interface SampleApi {

    /**
     * 登记样本（提前建档，无需实物）
     *
     * @param reqDTO 登记信息
     * @return 样本业务编号（sampleId）
     */
    String registerSample(SampleRegisterReqDTO reqDTO);

    /**
     * 更新样本状态（节点驱动流转；可顺带登记当前所在流程/节点）
     *
     * @param sampleId    样本业务编号
     * @param status      目标状态（八态，见 {@link SampleStatus}）
     * @param executionId 流程执行编号（可空）
     * @param nodeId      步骤节点编号（可空）
     */
    void updateStatus(String sampleId, String status, String executionId, String nodeId);

    /**
     * 读单个样本摘要（含实验参数，供节点执行读取）
     *
     * @param sampleId 样本业务编号
     * @return 样本摘要；不存在返回 null
     */
    SampleSummaryDTO getSample(String sampleId);

    /**
     * 批量读样本摘要（含实验参数）
     *
     * @param sampleIds 样本业务编号列表
     * @return 样本摘要列表（顺序与入参一致，不存在的样本跳过）
     */
    List<SampleSummaryDTO> getSamples(List<String> sampleIds);

}

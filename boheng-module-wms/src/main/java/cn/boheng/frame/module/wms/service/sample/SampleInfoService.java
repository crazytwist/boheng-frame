package cn.boheng.frame.module.wms.service.sample;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 样本 Service 接口
 *
 * <p>样本是「业务对象」，与实物（material_instance）分离。核心能力：登记（提前建档）、
 * 谱系维护（父/根/代数）、状态流转（八态）、流程关联（current_execution_id/node_id）。</p>
 *
 * @author yinan
 */
public interface SampleInfoService {

    /**
     * 登记样本（提前建档，无需实物）
     *
     * @param createReqVO 登记信息
     * @return 样本编号
     */
    Long createSample(@Valid SampleInfoSaveReqVO createReqVO);

    /**
     * 更新样本（属性级更新，不直接改状态，状态流转走 {@link #updateSampleStatus}）
     *
     * @param updateReqVO 更新信息
     */
    void updateSample(@Valid SampleInfoSaveReqVO updateReqVO);

    /**
     * 删除样本
     *
     * @param id 编号
     */
    void deleteSample(Long id);

    /**
     * 样本状态流转（供流程节点经契约层调用）
     *
     * @param sampleId     样本业务编号
     * @param status       目标状态（八态）
     * @param executionId  流程执行编号（可空，登记当前在哪个流程）
     * @param nodeId       步骤节点编号（可空）
     */
    void updateSampleStatus(String sampleId, String status, String executionId, String nodeId);

    /**
     * 获得样本
     *
     * @param id 编号
     * @return 样本
     */
    SampleInfoDO getSample(Long id);

    /**
     * 按样本业务编号获得样本
     *
     * @param sampleId 样本业务编号
     * @return 样本（不存在返回 null）
     */
    SampleInfoDO getSampleBySampleId(String sampleId);

    /**
     * 获得样本分页
     *
     * @param pageReqVO 分页查询
     * @return 样本分页
     */
    PageResult<SampleInfoDO> getSamplePage(SampleInfoPageReqVO pageReqVO);

    /**
     * 获得样本列表
     *
     * @return 样本列表
     */
    List<SampleInfoDO> getSampleList();
}

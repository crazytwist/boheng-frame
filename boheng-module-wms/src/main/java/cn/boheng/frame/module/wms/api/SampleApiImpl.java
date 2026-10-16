package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.SampleRegisterReqDTO;
import cn.boheng.frame.module.wms.api.dto.SampleSummaryDTO;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.service.sample.SampleInfoService;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 样本域契约层实现（SampleApi）
 *
 * <p>职责：把「跨模块契约」（SampleApi + DTO）翻译成 wms 内部 Service 调用，
 * 屏蔽 DO/VO 等实现细节。流程（flow）只依赖 {@link SampleApi}，本类由 Spring
 * 注入，对消费方透明。</p>
 *
 * <p>DTO ↔ VO ↔ DO 映射全部集中在本类，不暴露给外部模块。</p>
 *
 * @author yinan
 */
@Service
public class SampleApiImpl implements SampleApi {

    @Resource
    private SampleInfoService sampleInfoService;

    @Override
    public String registerSample(SampleRegisterReqDTO reqDTO) {
        SampleInfoSaveReqVO saveReqVO = new SampleInfoSaveReqVO();
        // 样本编号：调用方不传由 wms 生成（雪花，保证租户内唯一）
        saveReqVO.setSampleId(StrUtil.isBlank(reqDTO.getSampleId())
                ? "SP-" + IdUtil.getSnowflakeNextIdStr() : reqDTO.getSampleId());
        saveReqVO.setParentSampleId(reqDTO.getParentSampleId());
        saveReqVO.setDeriveType(reqDTO.getDeriveType());
        saveReqVO.setSampleName(reqDTO.getSampleName());
        saveReqVO.setSampleType(reqDTO.getSampleType());
        saveReqVO.setContentDefCode(reqDTO.getContentDefCode());
        saveReqVO.setContainerTypeCode(reqDTO.getContainerTypeCode());
        saveReqVO.setInitialVolUl(reqDTO.getInitialVolUl());
        saveReqVO.setExperimentParams(reqDTO.getExperimentParams());
        saveReqVO.setBatchNo(reqDTO.getBatchNo());
        saveReqVO.setOrderNo(reqDTO.getOrderNo());
        saveReqVO.setPriority(reqDTO.getPriority());
        saveReqVO.setSource(StrUtil.blankToDefault(reqDTO.getSource(), "MANUAL"));
        // 登记即「已登记」态；后续经 updateStatus 流转
        saveReqVO.setStatus(SampleStatus.REGISTERED);
        sampleInfoService.createSample(saveReqVO);
        return saveReqVO.getSampleId();
    }

    @Override
    public void updateStatus(String sampleId, String status, String executionId, String nodeId) {
        sampleInfoService.updateSampleStatus(sampleId, status, executionId, nodeId);
    }

    @Override
    public SampleSummaryDTO getSample(String sampleId) {
        return toSummary(sampleInfoService.getSampleBySampleId(sampleId));
    }

    @Override
    public List<SampleSummaryDTO> getSamples(List<String> sampleIds) {
        List<SampleSummaryDTO> result = new ArrayList<>(sampleIds == null ? 0 : sampleIds.size());
        if (sampleIds == null || sampleIds.isEmpty()) {
            return result;
        }
        for (String sampleId : sampleIds) {
            SampleInfoDO sample = sampleInfoService.getSampleBySampleId(sampleId);
            if (sample != null) {
                result.add(toSummary(sample));
            }
        }
        return result;
    }

    // ==================== DO → DTO ====================

    private SampleSummaryDTO toSummary(SampleInfoDO sample) {
        if (sample == null) {
            return null;
        }
        SampleSummaryDTO summary = new SampleSummaryDTO();
        summary.setId(sample.getId());
        summary.setSampleId(sample.getSampleId());
        summary.setSampleName(sample.getSampleName());
        summary.setSampleType(sample.getSampleType());
        summary.setStatus(sample.getStatus());
        summary.setContentDefCode(sample.getContentDefCode());
        summary.setContainerTypeCode(sample.getContainerTypeCode());
        summary.setInitialVolUl(sample.getInitialVolUl());
        summary.setExperimentParams(sample.getExperimentParams());
        summary.setCurrentExecutionId(sample.getCurrentExecutionId());
        summary.setCurrentNodeId(sample.getCurrentNodeId());
        summary.setBatchNo(sample.getBatchNo());
        return summary;
    }
}

package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.SampleDeriveRegisterReqDTO;
import cn.boheng.frame.module.wms.api.dto.SampleInstantiateReqDTO;
import cn.boheng.frame.module.wms.api.dto.SampleInstantiateRespDTO;
import cn.boheng.frame.module.wms.api.dto.SampleRegisterReqDTO;
import cn.boheng.frame.module.wms.api.dto.SampleSummaryDTO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceInstantiateReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceInstantiateRespVO;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.enums.SampleSourceEnum;
import cn.boheng.frame.module.wms.service.instance.MaterialInstanceService;
import cn.boheng.frame.module.wms.service.sample.SampleInfoService;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_DERIVE_PARENT_REQUIRED;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_DERIVE_TYPE_INVALID;

/**
 * 样本域契约层实现（SampleApi）
 */
@Service
public class SampleApiImpl implements SampleApi {

    @Resource
    private SampleInfoService sampleInfoService;

    @Resource
    private MaterialInstanceService materialInstanceService;

    @Override
    public String registerSample(SampleRegisterReqDTO reqDTO) {
        return registerOne(reqDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> registerSamples(List<SampleRegisterReqDTO> reqDTOs) {
        if (reqDTOs == null || reqDTOs.isEmpty()) {
            return List.of();
        }
        List<String> sampleIds = new ArrayList<>(reqDTOs.size());
        for (SampleRegisterReqDTO reqDTO : reqDTOs) {
            sampleIds.add(registerOne(reqDTO));
        }
        return sampleIds;
    }

    @Override
    public String registerDeriveSample(SampleDeriveRegisterReqDTO reqDTO) {
        if (reqDTO == null || StrUtil.isBlank(reqDTO.getParentSampleId())) {
            throw exception(SAMPLE_DERIVE_PARENT_REQUIRED);
        }
        if (StrUtil.isBlank(reqDTO.getDeriveType()) || "ORIGINAL".equals(reqDTO.getDeriveType())) {
            throw exception(SAMPLE_DERIVE_TYPE_INVALID);
        }
        SampleRegisterReqDTO register = new SampleRegisterReqDTO();
        register.setSampleId(reqDTO.getSampleId());
        register.setParentSampleId(reqDTO.getParentSampleId());
        register.setDeriveType(reqDTO.getDeriveType());
        register.setSampleName(reqDTO.getSampleName());
        register.setSampleType(reqDTO.getSampleType());
        register.setContentDefCode(reqDTO.getContentDefCode());
        register.setContainerTypeCode(reqDTO.getContainerTypeCode());
        register.setInitialVolUl(reqDTO.getInitialVolUl());
        register.setExperimentParams(reqDTO.getExperimentParams());
        register.setBatchNo(reqDTO.getBatchNo());
        register.setOrderNo(reqDTO.getOrderNo());
        register.setPriority(reqDTO.getPriority());
        register.setSource(reqDTO.getSource());
        return registerOne(register);
    }

    @Override
    public SampleInstantiateRespDTO instantiateSample(SampleInstantiateReqDTO reqDTO) {
        MaterialInstanceInstantiateReqVO reqVO = new MaterialInstanceInstantiateReqVO();
        reqVO.setSampleId(reqDTO.getSampleId());
        reqVO.setContainerTypeCode(reqDTO.getContainerTypeCode());
        reqVO.setQuantity(reqDTO.getQuantity());
        reqVO.setInitialVolUl(reqDTO.getInitialVolUl());
        reqVO.setIdempotentKey(reqDTO.getIdempotentKey());
        MaterialInstanceInstantiateRespVO vo = materialInstanceService.instantiateSample(reqVO);
        SampleInstantiateRespDTO dto = new SampleInstantiateRespDTO();
        dto.setSampleId(vo.getSampleId());
        dto.setInstanceIds(vo.getInstanceIds());
        dto.setOperationId(vo.getOperationId());
        dto.setIdempotentReplay(vo.getIdempotentReplay());
        return dto;
    }

    private String registerOne(SampleRegisterReqDTO reqDTO) {
        SampleInfoSaveReqVO saveReqVO = new SampleInfoSaveReqVO();
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
        saveReqVO.setSource(StrUtil.blankToDefault(reqDTO.getSource(), SampleSourceEnum.MANUAL.name()));
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

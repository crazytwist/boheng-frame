package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.MaterialCheckItemDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckItemResultDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeByContainerReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeItemDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialOperationTraceDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReleaseRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReqDTO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceCheckReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceCheckRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeByContainerReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReleaseRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MovementTraceBaseReqVO;
import cn.boheng.frame.module.wms.service.instance.MaterialInstanceService;
import cn.hutool.core.collection.CollUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料域契约层实现（MaterialApi）。
 *
 * <p>职责：把跨模块 DTO 翻译成 wms 内部 Service VO。Phase C+D 接通 check/use/consume/release。</p>
 */
@Service
public class MaterialApiImpl implements MaterialApi {

    @Resource
    private MaterialInstanceService materialInstanceService;

    @Override
    public MaterialCheckRespDTO check(MaterialCheckReqDTO reqDTO) {
        MaterialInstanceCheckReqVO reqVO = new MaterialInstanceCheckReqVO();
        List<MaterialInstanceCheckReqVO.Item> items = new ArrayList<>();
        if (CollUtil.isNotEmpty(reqDTO.getItems())) {
            for (MaterialCheckItemDTO itemDTO : reqDTO.getItems()) {
                MaterialInstanceCheckReqVO.Item item = new MaterialInstanceCheckReqVO.Item();
                item.setInstanceId(itemDTO.getInstanceId());
                item.setMinQty(itemDTO.getMinQty());
                item.setMinVolUl(itemDTO.getMinVolUl());
                items.add(item);
            }
        }
        reqVO.setItems(items);
        MaterialInstanceCheckRespVO vo = materialInstanceService.checkAvailability(reqVO);
        MaterialCheckRespDTO resp = new MaterialCheckRespDTO();
        resp.setOk(vo.getOk());
        List<MaterialCheckItemResultDTO> results = new ArrayList<>();
        if (CollUtil.isNotEmpty(vo.getItems())) {
            for (MaterialInstanceCheckRespVO.Item item : vo.getItems()) {
                MaterialCheckItemResultDTO r = new MaterialCheckItemResultDTO();
                r.setInstanceId(item.getInstanceId());
                r.setInstanceCode(item.getInstanceCode());
                r.setInstanceStatus(item.getInstanceStatus());
                r.setAvailable(item.getAvailable());
                r.setReason(item.getReason());
                results.add(r);
            }
        }
        resp.setItems(results);
        return resp;
    }

    @Override
    public MaterialUseReleaseRespDTO use(MaterialUseReqDTO reqDTO) {
        MaterialInstanceUseReqVO reqVO = new MaterialInstanceUseReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setInstanceId(reqDTO.getInstanceId());
        return toUseReleaseResp(materialInstanceService.use(reqVO));
    }

    @Override
    public MaterialConsumeRespDTO consume(MaterialConsumeReqDTO reqDTO) {
        MaterialInstanceConsumeReqVO reqVO = new MaterialInstanceConsumeReqVO();
        applyTrace(reqVO, reqDTO);
        List<MaterialInstanceConsumeReqVO.Item> items = new ArrayList<>();
        if (CollUtil.isNotEmpty(reqDTO.getItems())) {
            for (MaterialConsumeItemDTO itemDTO : reqDTO.getItems()) {
                MaterialInstanceConsumeReqVO.Item item = new MaterialInstanceConsumeReqVO.Item();
                item.setInstanceId(itemDTO.getInstanceId());
                item.setQty(itemDTO.getQty());
                item.setVolUl(itemDTO.getVolUl());
                item.setAfterStatus(itemDTO.getAfterStatus());
                item.setRemark(itemDTO.getRemark());
                items.add(item);
            }
        }
        reqVO.setItems(items);
        return toConsumeResp(materialInstanceService.consume(reqVO));
    }

    @Override
    public MaterialConsumeRespDTO consumeByContainer(MaterialConsumeByContainerReqDTO reqDTO) {
        MaterialInstanceConsumeByContainerReqVO reqVO = new MaterialInstanceConsumeByContainerReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setRootInstanceId(reqDTO.getRootInstanceId());
        reqVO.setCount(reqDTO.getCount());
        reqVO.setStrategy(reqDTO.getStrategy());
        reqVO.setAfterStatus(reqDTO.getAfterStatus());
        reqVO.setIncludeReserved(reqDTO.getIncludeReserved());
        return toConsumeResp(materialInstanceService.consumeByContainer(reqVO));
    }

    @Override
    public MaterialUseReleaseRespDTO release(MaterialReleaseReqDTO reqDTO) {
        MaterialInstanceReleaseReqVO reqVO = new MaterialInstanceReleaseReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setInstanceId(reqDTO.getInstanceId());
        return toUseReleaseResp(materialInstanceService.release(reqVO));
    }

    private static void applyTrace(MovementTraceBaseReqVO target, MaterialOperationTraceDTO source) {
        if (source == null) {
            return;
        }
        target.setRemark(source.getRemark());
        target.setBizSource(source.getBizSource());
        target.setOperatorType(source.getOperatorType());
        target.setRefType(source.getRefType());
        target.setRefId(source.getRefId());
        target.setRefNo(source.getRefNo());
        target.setIdempotentKey(source.getIdempotentKey());
        target.setOperateTime(source.getOperateTime());
        target.setOperationId(source.getOperationId());
    }

    private static MaterialConsumeRespDTO toConsumeResp(MaterialInstanceConsumeRespVO vo) {
        MaterialConsumeRespDTO dto = new MaterialConsumeRespDTO();
        if (vo == null) {
            return dto;
        }
        dto.setMovementIds(vo.getMovementIds());
        dto.setConsumedInstanceIds(vo.getConsumedInstanceIds());
        dto.setOperationId(vo.getOperationId());
        dto.setIdempotentReplay(vo.getIdempotentReplay());
        return dto;
    }

    private static MaterialUseReleaseRespDTO toUseReleaseResp(MaterialInstanceUseReleaseRespVO vo) {
        MaterialUseReleaseRespDTO dto = new MaterialUseReleaseRespDTO();
        if (vo == null) {
            return dto;
        }
        dto.setMovementId(vo.getMovementId());
        dto.setInstanceId(vo.getInstanceId());
        dto.setBeforeStatus(vo.getBeforeStatus());
        dto.setAfterStatus(vo.getAfterStatus());
        dto.setOperationId(vo.getOperationId());
        dto.setIdempotentReplay(vo.getIdempotentReplay());
        return dto;
    }

}

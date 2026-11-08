package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.*;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.*;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.InstanceStatusEnum;
import cn.boheng.frame.module.wms.enums.SlotStatusEnum;
import cn.boheng.frame.module.wms.service.instance.MaterialInstanceService;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料域契约层实现（MaterialApi）。W-2.1/W-2.2。
 */
@Service
public class MaterialApiImpl implements MaterialApi {

    @Resource
    private MaterialInstanceService materialInstanceService;

    @Resource
    private MaterialInstanceMapper materialInstanceMapper;

    @Resource
    private SlotMapper slotMapper;

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
    public MaterialAvailabilityRespDTO checkAvailability(MaterialAvailabilityReqDTO reqDTO) {
        MaterialAvailabilityRespDTO resp = new MaterialAvailabilityRespDTO();
        List<MaterialAvailabilityShortageDTO> items = new ArrayList<>();
        boolean ok = true;
        if (reqDTO != null && CollUtil.isNotEmpty(reqDTO.getItems())) {
            for (MaterialAvailabilityItemDTO item : reqDTO.getItems()) {
                items.add(evaluateAvailabilityItem(item));
            }
        }
        for (MaterialAvailabilityShortageDTO item : items) {
            if (!Boolean.TRUE.equals(item.getOk())) {
                ok = false;
                break;
            }
        }
        resp.setOk(ok);
        resp.setItems(items);
        return resp;
    }

    private MaterialAvailabilityShortageDTO evaluateAvailabilityItem(MaterialAvailabilityItemDTO item) {
        MaterialAvailabilityShortageDTO result = new MaterialAvailabilityShortageDTO();
        String kind = StrUtil.blankToDefault(item.getKind(), "").toUpperCase();
        result.setKind(kind);
        int required = item.getRequiredQty() == null || item.getRequiredQty() <= 0 ? 1 : item.getRequiredQty();
        result.setRequired(required);
        result.setRequiredVolUl(item.getRequiredVolUl());

        if ("SAMPLE".equals(kind)) {
            String sampleId = StrUtil.blankToDefault(item.getSampleId(), item.getCode());
            result.setCode(sampleId);
            long available = StrUtil.isBlank(sampleId) ? 0 : materialInstanceMapper.countAvailableBySampleId(sampleId);
            result.setAvailable((int) Math.min(available, Integer.MAX_VALUE));
            if (available < required) {
                result.setOk(Boolean.FALSE);
                result.setReason("样本可用实例不足：需要 " + required + "，可用 " + available);
            } else {
                result.setOk(Boolean.TRUE);
            }
            return result;
        }
        if ("CONTENT".equals(kind)) {
            String code = StrUtil.blankToDefault(item.getContentDefCode(), item.getCode());
            result.setCode(code);
            long available = StrUtil.isBlank(code) ? 0 : materialInstanceMapper.countAvailableByContentDefCode(code);
            result.setAvailable((int) Math.min(available, Integer.MAX_VALUE));
            if (available < required) {
                result.setOk(Boolean.FALSE);
                result.setReason("试剂可用实例不足：需要 " + required + "，可用 " + available);
            } else {
                result.setOk(Boolean.TRUE);
            }
            return result;
        }
        if ("CONTAINER".equals(kind)) {
            String code = StrUtil.blankToDefault(item.getContainerTypeCode(), item.getCode());
            result.setCode(code);
            long available = StrUtil.isBlank(code) ? 0 : materialInstanceMapper.countAvailableByContainerTypeCode(code);
            result.setAvailable((int) Math.min(available, Integer.MAX_VALUE));
            if (available < required) {
                result.setOk(Boolean.FALSE);
                result.setReason("容器可用实例不足：需要 " + required + "，可用 " + available);
            } else {
                result.setOk(Boolean.TRUE);
            }
            return result;
        }
        if ("SLOT".equals(kind)) {
            String slotType = StrUtil.blankToDefault(item.getSlotType(), item.getCode());
            result.setCode(slotType);
            String slotStatus = StrUtil.blankToDefault(item.getSlotStatus(), SlotStatusEnum.FREE.name());
            List<SlotDO> slots = slotMapper.selectListForMaterialQuery(
                    item.getZoneCode(), item.getDeviceCode(), slotType, slotStatus, 0, required);
            int available = slots == null ? 0 : slots.size();
            result.setAvailable(available);
            if (available < required) {
                result.setOk(Boolean.FALSE);
                result.setReason("合适槽位不足：需要 " + required + "，可用 " + available);
            } else {
                result.setOk(Boolean.TRUE);
            }
            return result;
        }
        result.setCode(item.getCode());
        result.setAvailable(0);
        result.setOk(Boolean.FALSE);
        result.setReason("未知检查类型：" + kind);
        return result;
    }

    @Override
    public List<MaterialSlotDTO> listSlots(MaterialSlotQueryReqDTO reqDTO) {
        int limit = reqDTO == null || reqDTO.getLimit() == null ? 50 : reqDTO.getLimit();
        List<SlotDO> slots = slotMapper.selectListForMaterialQuery(
                reqDTO == null ? null : reqDTO.getZoneCode(),
                reqDTO == null ? null : reqDTO.getDeviceCode(),
                reqDTO == null ? null : reqDTO.getSlotType(),
                reqDTO == null ? null : reqDTO.getSlotStatus(),
                reqDTO == null ? null : reqDTO.getStatus(),
                limit);
        List<MaterialSlotDTO> result = new ArrayList<>();
        if (slots == null) {
            return result;
        }
        for (SlotDO slot : slots) {
            MaterialSlotDTO dto = new MaterialSlotDTO();
            dto.setId(slot.getId());
            dto.setSlotCode(slot.getSlotCode());
            dto.setZoneCode(slot.getZoneCode());
            dto.setDeviceCode(slot.getDeviceCode());
            dto.setSlotType(slot.getSlotType());
            dto.setSlotStatus(slot.getSlotStatus());
            dto.setStatus(slot.getStatus());
            dto.setCapacity(slot.getCapacity());
            dto.setOccupiedQty(slot.getOccupiedQty());
            result.add(dto);
        }
        return result;
    }

    @Override
    public MaterialReserveRespDTO reserve(MaterialReserveReqDTO reqDTO) {
        MaterialInstanceReserveReqVO reqVO = new MaterialInstanceReserveReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setExecutionId(reqDTO.getExecutionId());
        reqVO.setInstanceIds(reqDTO.getInstanceIds());
        reqVO.setReservedUntil(reqDTO.getReservedUntil());
        MaterialInstanceReserveRespVO vo = materialInstanceService.reserve(reqVO);
        MaterialReserveRespDTO dto = new MaterialReserveRespDTO();
        dto.setReservationId(vo.getReservationId());
        dto.setInstanceIds(vo.getInstanceIds());
        dto.setMovementIds(vo.getMovementIds());
        dto.setOperationId(vo.getOperationId());
        dto.setIdempotentReplay(vo.getIdempotentReplay());
        return dto;
    }

    @Override
    public MaterialUseReleaseRespDTO use(MaterialUseReqDTO reqDTO) {
        MaterialInstanceUseReqVO reqVO = new MaterialInstanceUseReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setInstanceId(reqDTO.getInstanceId());
        reqVO.setReservationId(reqDTO.getReservationId());
        reqVO.setExecutionId(reqDTO.getExecutionId());
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
    public MaterialConsumeRespDTO consumeReservation(MaterialConsumeReservationReqDTO reqDTO) {
        MaterialInstanceConsumeReservationReqVO reqVO = new MaterialInstanceConsumeReservationReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setReservationId(reqDTO.getReservationId());
        reqVO.setExecutionId(reqDTO.getExecutionId());
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
        return toConsumeResp(materialInstanceService.consumeReservation(reqVO));
    }

    @Override
    public MaterialUseReleaseRespDTO release(MaterialReleaseReqDTO reqDTO) {
        MaterialInstanceReleaseReqVO reqVO = new MaterialInstanceReleaseReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setInstanceId(reqDTO.getInstanceId());
        reqVO.setReservationId(reqDTO.getReservationId());
        reqVO.setExecutionId(reqDTO.getExecutionId());
        return toUseReleaseResp(materialInstanceService.release(reqVO));
    }

    @Override
    public MaterialReleaseByExecutionRespDTO releaseByExecution(MaterialReleaseByExecutionReqDTO reqDTO) {
        MaterialInstanceReleaseByExecutionReqVO reqVO = new MaterialInstanceReleaseByExecutionReqVO();
        applyTrace(reqVO, reqDTO);
        reqVO.setExecutionId(reqDTO.getExecutionId());
        reqVO.setReason(reqDTO.getReason());
        MaterialInstanceReleaseByExecutionRespVO vo = materialInstanceService.releaseByExecution(reqVO);
        MaterialReleaseByExecutionRespDTO dto = new MaterialReleaseByExecutionRespDTO();
        dto.setReleasedInstanceIds(vo.getReleasedInstanceIds());
        dto.setMovementIds(vo.getMovementIds());
        dto.setOperationId(vo.getOperationId());
        dto.setIdempotentReplay(vo.getIdempotentReplay());
        List<MaterialPendingReleaseDTO> pending = new ArrayList<>();
        if (CollUtil.isNotEmpty(vo.getPendingInUse())) {
            for (MaterialInstanceReleaseByExecutionRespVO.Pending p : vo.getPendingInUse()) {
                MaterialPendingReleaseDTO d = new MaterialPendingReleaseDTO();
                d.setInstanceId(p.getInstanceId());
                d.setInstanceCode(p.getInstanceCode());
                d.setInstanceStatus(p.getInstanceStatus());
                d.setReason(p.getReason());
                pending.add(d);
            }
        }
        dto.setPendingInUse(pending);
        return dto;
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

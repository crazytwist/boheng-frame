package cn.boheng.frame.module.wms.service.app;

import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTakeOutReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTransferReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.TipConsumeReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.TipResourceRespVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialIdQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialItem;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialLiquidTypeQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPosQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPositionAreaQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPositionAreaRespVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialStatusTypeQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialUpdateCmdReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.container.ContainerTypeMapper;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.sample.SampleInfoMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.dal.mysql.zone.ZoneMapper;
import cn.boheng.frame.module.wms.enums.external.ExLabwareTypeEnum;
import cn.boheng.frame.module.wms.enums.external.ExLiquidTypeEnum;
import cn.boheng.frame.module.wms.service.instance.MaterialInstanceService;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.CONTAINER_TYPE_NOT_EXISTS;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MATERIAL_INSTANCE_NOT_EXISTS;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SLOT_PLACE_NOT_FOUND;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.TIP_INSTANCE_REQUIRED;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.INSTANCE_CONSUME_EXCEED;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.ZONE_NOT_EXISTS;

@Slf4j
@Service
@Validated
public class AppMaterialInstanceServiceImpl implements AppMaterialInstanceService {

    private static final String STATUS_DISCARDED = "DISCARDED";
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String SLOT_STATUS_FREE = "FREE";

    @Resource
    private ZoneMapper zoneMapper;
    @Resource
    private SlotMapper slotMapper;
    @Resource
    private MaterialInstanceMapper materialInstanceMapper;
    @Resource
    private ContainerTypeMapper containerTypeMapper;
    @Resource
    private SampleInfoMapper sampleInfoMapper;
    @Resource
    private MaterialInstanceService materialInstanceService;

    @Override
    public List<MaterialItem> listByPos(MaterialPosQueryReqVO query) {
        List<MaterialItem> items = new ArrayList<>();
        for (String pos : query.getPosList()) {
            MaterialItem item = buildItemByPos(pos);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<MaterialItem> listByLiquidAndType(MaterialLiquidTypeQueryReqVO query) {
        requireZone(query.getPositionArea());
        Set<Long> allowedSlotIds = slotIdsInZone(query.getPositionArea());
        if (allowedSlotIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SlotDO> slotById = loadSlotsByIds(allowedSlotIds);

        ExLiquidTypeEnum liquidTypeEnum = ExLiquidTypeEnum.fromCode(query.getLiquidType());
        LambdaQueryWrapperX<MaterialInstanceDO> wrapper = (LambdaQueryWrapperX<MaterialInstanceDO>) new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getContentDefCode, liquidTypeEnum.name())
                .and(w -> w.isNull(MaterialInstanceDO::getCurrentVolUl)
                        .or()
                        .ge(MaterialInstanceDO::getCurrentVolUl, BigDecimal.ZERO));
        if (StrUtil.isNotBlank(query.getBatchId())) {
            wrapper.eq(MaterialInstanceDO::getDescription, query.getBatchId());
        }
        List<MaterialInstanceDO> candidates = materialInstanceMapper.selectList(wrapper);
        if (CollUtil.isEmpty(candidates)) {
            return List.of();
        }

        Map<Long, MaterialInstanceDO> instanceById = MaterialItemConvertHelper.indexById(candidates);
        loadMissingParents(instanceById);
        Map<Long, ContainerTypeDO> containerTypeById = loadContainerTypes(candidates, instanceById);
        Map<String, SampleInfoDO> sampleBySampleId = loadSamples(candidates);

        List<MaterialItem> items = new ArrayList<>();
        for (MaterialInstanceDO instance : candidates) {
            Long rootSlotId = resolveRootSlotId(instance, instanceById);
            if (rootSlotId == null || !allowedSlotIds.contains(rootSlotId)) {
                continue;
            }
            items.add(convertOne(instance, instanceById, slotById, containerTypeById, sampleBySampleId));
        }
        return items;
    }

    @Override
    public List<MaterialItem> listByStatusAndType(MaterialStatusTypeQueryReqVO query) {
        requireZone(query.getPositionArea());
        Set<Long> slotIds = slotIdsInZone(query.getPositionArea());
        if (slotIds.isEmpty()) {
            return List.of();
        }

        ExLabwareTypeEnum typeEnum = ExLabwareTypeEnum.fromCode(query.getLabwareType());
        String internalStatus = MaterialItemConvertHelper.toInternalStatus(query.getLabwareStatus());

        if (typeEnum == ExLabwareTypeEnum.LabwareType_50CentrifugeTube_Board) {
            MaterialInstanceDO carrier = findTopInSlots(slotIds, internalStatus, typeEnum.name());
            if (carrier == null) {
                return List.of();
            }
            MaterialIdQueryReqVO idQuery = new MaterialIdQueryReqVO();
            idQuery.setLabwareIdList(List.of(carrier.getId()));
            return listByIds(idQuery);
        }

        ContainerTypeDO containerType = containerTypeMapper.selectByTypeCode(typeEnum.name());
        if (containerType == null) {
            throw exception(CONTAINER_TYPE_NOT_EXISTS);
        }

        LambdaQueryWrapperX<MaterialInstanceDO> wrapper = (LambdaQueryWrapperX<MaterialInstanceDO>) new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getContainerTypeCode, typeEnum.name())
                .eq(MaterialInstanceDO::getInstanceStatus, internalStatus)
                .isNull(MaterialInstanceDO::getParentInstanceId)
                .in(MaterialInstanceDO::getRootSlotId, slotIds)
                .ne(MaterialInstanceDO::getInstanceStatus, STATUS_DISCARDED);
        return convertList(materialInstanceMapper.selectList(wrapper));
    }

    @Override
    public List<MaterialItem> listByIds(MaterialIdQueryReqVO query) {
        List<MaterialItem> items = new ArrayList<>();
        for (Long id : query.getLabwareIdList()) {
            MaterialInstanceDO instance = materialInstanceMapper.selectById(id);
            if (instance == null) {
                throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
            }
            MaterialItem item = buildItemForInstance(instance, null);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public MaterialPositionAreaRespVO queryPositionByArea(MaterialPositionAreaQueryReqVO areaQuery) {
        int need = areaQuery.getCount() == null ? 0 : areaQuery.getCount();
        if (need <= 0) {
            return MaterialPositionAreaRespVO.empty();
        }
        ExLabwareTypeEnum typeEnum = ExLabwareTypeEnum.fromCode(areaQuery.getLabwareType());
        String containerTypeCode = typeEnum.name();

        if (areaQuery.getPositionArea().contains("|")) {
            return queryCarrierInnerPositions(areaQuery.getPositionArea(), need);
        }

        if (zoneMapper.selectByZoneCode(areaQuery.getPositionArea()) == null) {
            log.error("位置不存在:{}", areaQuery.getPositionArea());
            return MaterialPositionAreaRespVO.empty();
        }

        List<SlotDO> slots = slotMapper.selectListByZoneCode(areaQuery.getPositionArea());
        List<String> matched = new ArrayList<>();
        for (SlotDO slot : slots) {
            if (!isSlotAvailable(slot)) {
                continue;
            }
            if (!SlotSuitableMaterialMatcher.supportsContainerType(slot.getSuitableMaterials(), containerTypeCode)) {
                continue;
            }
            matched.add(slot.getSlotCode());
            if (matched.size() >= need) {
                break;
            }
        }
        if (matched.size() < need) {
            log.error("位置数量不足:{},需要{}个", areaQuery.getPositionArea(), need);
            return MaterialPositionAreaRespVO.empty();
        }
        return new MaterialPositionAreaRespVO(matched);
    }

    private MaterialPositionAreaRespVO queryCarrierInnerPositions(String positionArea, int need) {
        String[] split = positionArea.split("\\|", 2);
        SlotDO carrierSlot = resolvePrimarySlot(split[0]);
        MaterialInstanceDO carrier = findTopInstanceOnSlot(carrierSlot.getId());
        if (carrier == null) {
            log.error("载具不存在:{}", split[0]);
            return MaterialPositionAreaRespVO.empty();
        }
        ContainerTypeDO carrierType = carrier.getContainerTypeId() != null
                ? containerTypeMapper.selectById(carrier.getContainerTypeId()) : null;
        if (carrierType == null || !MaterialItemConvertHelper.isCarrierBoard(carrierType)) {
            return MaterialPositionAreaRespVO.empty();
        }
        List<String> allPos = CarrierPositionCodes.build(carrierType);
        List<String> freePos = new ArrayList<>();
        for (String pos : allPos) {
            MaterialInstanceDO child = findChildOnCarrier(carrier.getId(), pos);
            if (child == null || STATUS_DISCARDED.equals(child.getInstanceStatus()) || isReusableEmptyChild(child)) {
                freePos.add(pos);
            }
            if (freePos.size() >= need) {
                break;
            }
        }
        if (freePos.size() < need) {
            log.error("载具孔位数量不足:{},需要{}个", positionArea, need);
            return MaterialPositionAreaRespVO.empty();
        }
        return new MaterialPositionAreaRespVO(freePos.subList(0, need));
    }

    private boolean isReusableEmptyChild(MaterialInstanceDO child) {
        return "EMPTY".equalsIgnoreCase(child.getContentType())
                || child.getCurrentVolUl() == null
                || child.getCurrentVolUl().compareTo(BigDecimal.ZERO) <= 0;
    }

    private boolean isSlotAvailable(SlotDO slot) {
        if (slot.getStatus() != null && slot.getStatus() == 1) {
            return false;
        }
        return SLOT_STATUS_FREE.equals(slot.getSlotStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateLabware(List<MaterialUpdateCmdReqVO> updateCmdList) {
        for (MaterialUpdateCmdReqVO cmd : updateCmdList) {
            MaterialInstanceDO instance = materialInstanceMapper.selectById(cmd.getLabwareId());
            if (instance == null) {
                throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
            }
            if (StrUtil.isNotBlank(cmd.getPosition())) {
                applyPosition(instance, cmd.getPosition());
                instance = materialInstanceMapper.selectById(instance.getId());
            }
            applyAttributeUpdates(instance, cmd);
        }
        return true;
    }

    private MaterialItem buildItemByPos(String pos) {
        if (StrUtil.isBlank(pos)) {
            return null;
        }
        String locationKey = pos;
        String childPosFilter = "";
        if (pos.contains("|")) {
            String[] split = pos.split("\\|", 2);
            locationKey = split[0];
            childPosFilter = split[1];
        }
        SlotDO slot = resolvePrimarySlot(locationKey);
        MaterialInstanceDO top = findTopInstanceOnSlot(slot.getId());
        if (top == null) {
            return null;
        }
        if (StrUtil.isNotBlank(childPosFilter)) {
            MaterialInstanceDO child = findChildOnCarrier(top.getId(), childPosFilter);
            return child != null ? buildItemForInstance(child, childPosFilter) : null;
        }
        return buildItemForInstance(top, null);
    }

    private MaterialItem buildItemForInstance(MaterialInstanceDO instance, String childPosFilter) {
        ContainerTypeDO containerType = instance.getContainerTypeId() != null
                ? containerTypeMapper.selectById(instance.getContainerTypeId()) : null;
        if (MaterialItemConvertHelper.isCarrierBoard(containerType) && StrUtil.isBlank(childPosFilter)) {
            SlotDO rootSlot = instance.getRootSlotId() != null ? slotMapper.selectById(instance.getRootSlotId()) : null;
            List<MaterialInstanceDO> children = listChildren(instance.getId(), null);
            Map<Long, ContainerTypeDO> containerTypeById = loadContainerTypes(children, Map.of());
            Map<String, SampleInfoDO> sampleBySampleId = loadSamples(children);
            return MaterialItemConvertHelper.toCarrierMaterialItem(instance, rootSlot, containerType, children,
                    containerTypeById, sampleBySampleId);
        }
        return convertSingleInstance(instance);
    }

    private MaterialItem convertSingleInstance(MaterialInstanceDO instance) {
        Map<Long, MaterialInstanceDO> instanceById = new HashMap<>();
        instanceById.put(instance.getId(), instance);
        loadMissingParents(instanceById);
        MaterialInstanceDO parent = instance.getParentInstanceId() != null
                ? instanceById.get(instance.getParentInstanceId()) : null;
        Long rootSlotId = resolveRootSlotId(instance, instanceById);
        SlotDO rootSlot = rootSlotId != null ? slotMapper.selectById(rootSlotId) : null;
        ContainerTypeDO containerType = instance.getContainerTypeId() != null
                ? containerTypeMapper.selectById(instance.getContainerTypeId()) : null;
        SampleInfoDO sample = StrUtil.isNotBlank(instance.getSampleId())
                ? sampleInfoMapper.selectBySampleId(instance.getSampleId()) : null;
        return MaterialItemConvertHelper.toMaterialItem(instance, parent, rootSlot, containerType, sample);
    }

    private List<MaterialItem> convertList(List<MaterialInstanceDO> instances) {
        if (CollUtil.isEmpty(instances)) {
            return List.of();
        }
        Map<Long, MaterialInstanceDO> instanceById = MaterialItemConvertHelper.indexById(instances);
        loadMissingParents(instanceById);
        Set<Long> slotIds = instances.stream()
                .map(i -> resolveRootSlotId(i, instanceById))
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, SlotDO> slotById = loadSlotsByIds(slotIds);
        Map<Long, ContainerTypeDO> containerTypeById = loadContainerTypes(instances, instanceById);
        Map<String, SampleInfoDO> sampleBySampleId = loadSamples(instances);
        List<MaterialItem> items = new ArrayList<>(instances.size());
        for (MaterialInstanceDO instance : instances) {
            items.add(convertOne(instance, instanceById, slotById, containerTypeById, sampleBySampleId));
        }
        return items;
    }

    private MaterialItem convertOne(MaterialInstanceDO instance,
                                    Map<Long, MaterialInstanceDO> instanceById,
                                    Map<Long, SlotDO> slotById,
                                    Map<Long, ContainerTypeDO> containerTypeById,
                                    Map<String, SampleInfoDO> sampleBySampleId) {
        MaterialInstanceDO parent = instance.getParentInstanceId() != null
                ? instanceById.get(instance.getParentInstanceId()) : null;
        Long rootSlotId = resolveRootSlotId(instance, instanceById);
        SlotDO rootSlot = rootSlotId != null ? slotById.get(rootSlotId) : null;
        ContainerTypeDO containerType = containerTypeById.get(instance.getContainerTypeId());
        SampleInfoDO sample = StrUtil.isNotBlank(instance.getSampleId())
                ? sampleBySampleId.get(instance.getSampleId()) : null;
        return MaterialItemConvertHelper.toMaterialItem(instance, parent, rootSlot, containerType, sample);
    }

    private void applyPosition(MaterialInstanceDO instance, String position) {
        if (position.contains("|")) {
            String[] split = position.split("\\|", 2);
            SlotDO carrierSlot = resolvePrimarySlot(split[0]);
            MaterialInstanceDO carrier = findTopInstanceOnSlot(carrierSlot.getId());
            if (carrier == null) {
                throw exception(SLOT_PLACE_NOT_FOUND, split[0]);
            }
            detachFromSlotIfNeeded(instance);
            materialInstanceMapper.update(null, new LambdaUpdateWrapper<MaterialInstanceDO>()
                    .eq(MaterialInstanceDO::getId, instance.getId())
                    .set(MaterialInstanceDO::getParentInstanceId, carrier.getId())
                    .set(MaterialInstanceDO::getParentPositionCode, split[1])
                    .set(MaterialInstanceDO::getRootSlotId, null)
                    .set(MaterialInstanceDO::getRootSlotCode, null));
            return;
        }
        SlotDO targetSlot = resolvePrimarySlot(position);
        if (instance.getParentInstanceId() != null) {
            materialInstanceMapper.update(null, new LambdaUpdateWrapper<MaterialInstanceDO>()
                    .eq(MaterialInstanceDO::getId, instance.getId())
                    .set(MaterialInstanceDO::getParentInstanceId, null)
                    .set(MaterialInstanceDO::getParentPositionCode, null));
            MaterialInstancePutInReqVO putIn = new MaterialInstancePutInReqVO();
            putIn.setInstanceId(instance.getId());
            putIn.setSlotId(targetSlot.getId());
            materialInstanceService.putIn(putIn);
            return;
        }
        if (instance.getRootSlotId() == null) {
            MaterialInstancePutInReqVO putIn = new MaterialInstancePutInReqVO();
            putIn.setInstanceId(instance.getId());
            putIn.setSlotId(targetSlot.getId());
            materialInstanceService.putIn(putIn);
            return;
        }
        if (!instance.getRootSlotId().equals(targetSlot.getId())) {
            MaterialInstanceTransferReqVO transfer = new MaterialInstanceTransferReqVO();
            transfer.setInstanceId(instance.getId());
            transfer.setTargetSlotId(targetSlot.getId());
            materialInstanceService.transfer(transfer);
        }
    }

    private void detachFromSlotIfNeeded(MaterialInstanceDO instance) {
        if (instance.getRootSlotId() == null || instance.getParentInstanceId() != null) {
            return;
        }
        MaterialInstanceTakeOutReqVO takeOut = new MaterialInstanceTakeOutReqVO();
        takeOut.setInstanceId(instance.getId());
        materialInstanceService.takeOut(takeOut);
    }

    private void applyAttributeUpdates(MaterialInstanceDO instance, MaterialUpdateCmdReqVO cmd) {
        LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId());
        boolean changed = false;

        if (cmd.getLiquidType() != null) {
            ExLiquidTypeEnum liquid = ExLiquidTypeEnum.fromCode(cmd.getLiquidType());
            update.set(MaterialInstanceDO::getContentDefCode, liquid.name());
            changed = true;
        }
        if (cmd.getVolume() != null) {
            update.set(MaterialInstanceDO::getCurrentVolUl, BigDecimal.valueOf(cmd.getVolume()));
            changed = true;
        }
        if (cmd.getQuantity() != null) {
            update.set(MaterialInstanceDO::getCurrentCount, cmd.getQuantity());
            changed = true;
        }
        if (cmd.getBatchId() != null) {
            update.set(MaterialInstanceDO::getDescription, cmd.getBatchId().toString());
            changed = true;
        }
        if (cmd.getLabwareType() != null) {
            ExLabwareTypeEnum labwareType = ExLabwareTypeEnum.fromCode(cmd.getLabwareType());
            ContainerTypeDO containerType = containerTypeMapper.selectByTypeCode(labwareType.name());
            if (containerType == null) {
                throw exception(CONTAINER_TYPE_NOT_EXISTS);
            }
            update.set(MaterialInstanceDO::getContainerTypeId, containerType.getId());
            update.set(MaterialInstanceDO::getContainerTypeCode, containerType.getTypeCode());
            changed = true;
        }
        if (cmd.getStatus() != null) {
            update.set(MaterialInstanceDO::getInstanceStatus, MaterialItemConvertHelper.toInternalStatus(cmd.getStatus()));
            changed = true;
        }
        if (changed) {
            materialInstanceMapper.update(null, update);
        }
    }

    private void requireZone(String zoneCode) {
        if (zoneMapper.selectByZoneCode(zoneCode) == null) {
            throw exception(ZONE_NOT_EXISTS);
        }
    }

    private Set<Long> slotIdsInZone(String zoneCode) {
        List<SlotDO> slots = slotMapper.selectListByZoneCode(zoneCode);
        return slots.stream().map(SlotDO::getId).collect(Collectors.toSet());
    }

    private SlotDO resolvePrimarySlot(String locationKey) {
        SlotDO byCode = slotMapper.selectBySlotCode(locationKey);
        if (byCode != null) {
            return byCode;
        }
        requireZone(locationKey);
        List<SlotDO> slots = slotMapper.selectListByZoneCode(locationKey);
        if (CollUtil.isEmpty(slots)) {
            throw exception(SLOT_PLACE_NOT_FOUND, locationKey);
        }
        for (SlotDO slot : slots) {
            if (findTopInstanceOnSlot(slot.getId()) != null) {
                return slot;
            }
        }
        return slots.get(0);
    }

    private MaterialInstanceDO findTopInstanceOnSlot(Long slotId) {
        return materialInstanceMapper.selectOne(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getRootSlotId, slotId)
                .isNull(MaterialInstanceDO::getParentInstanceId)
                .ne(MaterialInstanceDO::getInstanceStatus, STATUS_DISCARDED)
                .last("LIMIT 1"));
    }

    private MaterialInstanceDO findTopInSlots(Set<Long> slotIds, String status, String containerTypeCode) {
        return materialInstanceMapper.selectOne(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getContainerTypeCode, containerTypeCode)
                .eq(MaterialInstanceDO::getInstanceStatus, status)
                .isNull(MaterialInstanceDO::getParentInstanceId)
                .in(MaterialInstanceDO::getRootSlotId, slotIds)
                .ne(MaterialInstanceDO::getInstanceStatus, STATUS_DISCARDED)
                .last("LIMIT 1"));
    }

    private MaterialInstanceDO findChildOnCarrier(Long carrierId, String parentPositionCode) {
        return materialInstanceMapper.selectOne(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getParentInstanceId, carrierId)
                .eq(MaterialInstanceDO::getParentPositionCode, parentPositionCode)
                .ne(MaterialInstanceDO::getInstanceStatus, STATUS_DISCARDED)
                .last("LIMIT 1"));
    }

    private List<MaterialInstanceDO> listChildren(Long parentId, String parentPositionCode) {
        return materialInstanceMapper.selectList(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getParentInstanceId, parentId)
                .eqIfPresent(MaterialInstanceDO::getParentPositionCode, parentPositionCode)
                .ne(MaterialInstanceDO::getInstanceStatus, STATUS_DISCARDED));
    }

    private Map<Long, SlotDO> loadSlotsByIds(Set<Long> slotIds) {
        if (CollUtil.isEmpty(slotIds)) {
            return Map.of();
        }
        List<SlotDO> slots = slotMapper.selectList(new LambdaQueryWrapperX<SlotDO>()
                .in(SlotDO::getId, slotIds));
        Map<Long, SlotDO> map = new HashMap<>();
        for (SlotDO slot : slots) {
            map.put(slot.getId(), slot);
        }
        return map;
    }

    private void loadMissingParents(Map<Long, MaterialInstanceDO> instanceById) {
        Set<Long> parentIds = new HashSet<>();
        for (MaterialInstanceDO instance : instanceById.values()) {
            if (instance.getParentInstanceId() != null && !instanceById.containsKey(instance.getParentInstanceId())) {
                parentIds.add(instance.getParentInstanceId());
            }
        }
        if (parentIds.isEmpty()) {
            return;
        }
        List<MaterialInstanceDO> parents = materialInstanceMapper.selectList(
                new LambdaQueryWrapperX<MaterialInstanceDO>().in(MaterialInstanceDO::getId, parentIds));
        for (MaterialInstanceDO parent : parents) {
            instanceById.put(parent.getId(), parent);
        }
    }

    private Map<Long, ContainerTypeDO> loadContainerTypes(List<MaterialInstanceDO> candidates,
                                                          Map<Long, MaterialInstanceDO> instanceById) {
        Set<Long> typeIds = new HashSet<>();
        for (MaterialInstanceDO instance : candidates) {
            if (instance.getContainerTypeId() != null) {
                typeIds.add(instance.getContainerTypeId());
            }
        }
        for (MaterialInstanceDO instance : instanceById.values()) {
            if (instance.getContainerTypeId() != null) {
                typeIds.add(instance.getContainerTypeId());
            }
        }
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        List<ContainerTypeDO> types = containerTypeMapper.selectList(
                new LambdaQueryWrapperX<ContainerTypeDO>().in(ContainerTypeDO::getId, typeIds));
        Map<Long, ContainerTypeDO> map = new HashMap<>();
        for (ContainerTypeDO type : types) {
            map.put(type.getId(), type);
        }
        return map;
    }

    private Map<String, SampleInfoDO> loadSamples(List<MaterialInstanceDO> candidates) {
        Set<String> sampleIds = candidates.stream()
                .map(MaterialInstanceDO::getSampleId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (sampleIds.isEmpty()) {
            return Map.of();
        }
        List<SampleInfoDO> samples = sampleInfoMapper.selectList(
                new LambdaQueryWrapperX<SampleInfoDO>().in(SampleInfoDO::getSampleId, sampleIds));
        Map<String, SampleInfoDO> map = new HashMap<>();
        for (SampleInfoDO sample : samples) {
            map.put(sample.getSampleId(), sample);
        }
        return map;
    }

    private Long resolveRootSlotId(MaterialInstanceDO instance, Map<Long, MaterialInstanceDO> instanceById) {
        MaterialInstanceDO cur = instance;
        int guard = 0;
        while (cur != null && cur.getRootSlotId() == null && cur.getParentInstanceId() != null && guard++ < 16) {
            Long parentId = cur.getParentInstanceId();
            cur = instanceById.get(parentId);
            if (cur == null) {
                cur = materialInstanceMapper.selectById(parentId);
                if (cur != null) {
                    instanceById.put(cur.getId(), cur);
                }
            }
        }
        if (cur == null) {
            return null;
        }
        return cur.getRootSlotId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean tipConsume(TipConsumeReqVO cmd) {
        MaterialInstanceDO instance = materialInstanceMapper.selectById(cmd.getId());
        if (instance == null) {
            throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
        }
        ContainerTypeDO containerType = requireTipContainer(instance);
        int remain = TipInstanceSupport.currentCount(instance);
        if (remain < cmd.getCount()) {
            throw exception(INSTANCE_CONSUME_EXCEED, instance.getInstanceCode(), cmd.getCount(), remain);
        }
        MaterialInstanceConsumeReqVO consumeReq = new MaterialInstanceConsumeReqVO();
        MaterialInstanceConsumeReqVO.Item item = new MaterialInstanceConsumeReqVO.Item();
        item.setInstanceId(instance.getId());
        item.setQty(cmd.getCount());
        consumeReq.setItems(List.of(item));
        materialInstanceService.consume(consumeReq);
        return true;
    }

    @Override
    public TipResourceRespVO queryTipResource(String areaCode) {
        if (zoneMapper.selectByZoneCode(areaCode) == null && slotMapper.selectBySlotCode(areaCode) == null) {
            log.error("位置不存在:{}", areaCode);
            return null;
        }
        SlotDO slot;
        try {
            slot = resolvePrimarySlot(areaCode);
        } catch (Exception e) {
            log.error("位置不存在:{}", areaCode);
            return null;
        }
        MaterialInstanceDO instance = findTopInstanceOnSlot(slot.getId());
        if (instance == null) {
            log.error("该位置物料不存在:{}", areaCode);
            return null;
        }
        ContainerTypeDO containerType = containerTypeMapper.selectById(instance.getContainerTypeId());
        if (!TipInstanceSupport.isTipContainer(containerType)) {
            log.error("该位置物料不是 Tip 盒:{}", areaCode);
            return null;
        }
        TipResourceRespVO resp = new TipResourceRespVO();
        resp.setId(String.valueOf(instance.getId()));
        resp.setName(StrUtil.blankToDefault(instance.getInstanceName(), instance.getInstanceCode()));
        resp.setCapacity(TipInstanceSupport.currentCount(instance));
        resp.setRangeMax(TipInstanceSupport.maxCount(instance, containerType));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean tipComplement(String tipId) {
        Long instanceId;
        try {
            instanceId = Long.parseLong(tipId);
        } catch (NumberFormatException e) {
            throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
        }
        MaterialInstanceDO instance = materialInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
        }
        ContainerTypeDO containerType = requireTipContainer(instance);
        int max = TipInstanceSupport.maxCount(instance, containerType);
        materialInstanceMapper.update(null, new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .set(MaterialInstanceDO::getCurrentCount, max)
                .set(MaterialInstanceDO::getInstanceStatus, STATUS_AVAILABLE));
        return true;
    }

    private ContainerTypeDO requireTipContainer(MaterialInstanceDO instance) {
        ContainerTypeDO containerType = instance.getContainerTypeId() != null
                ? containerTypeMapper.selectById(instance.getContainerTypeId()) : null;
        if (!TipInstanceSupport.isTipContainer(containerType)) {
            throw exception(TIP_INSTANCE_REQUIRED, instance.getId());
        }
        return containerType;
    }
}

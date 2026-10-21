package cn.boheng.frame.module.wms.service.app;

import cn.boheng.frame.module.wms.controller.app.vo.MaterialItem;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.enums.external.ExLabwareStatusEnum;
import cn.boheng.frame.module.wms.enums.external.ExLabwareTypeEnum;
import cn.boheng.frame.module.wms.enums.external.ExLiquidTypeEnum;
import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物料实例 → 外部 {@link MaterialItem} 视图转换
 */
final class MaterialItemConvertHelper {

    private MaterialItemConvertHelper() {
    }

    static String toInternalStatus(Integer externalStatusCode) {
        if (externalStatusCode == null) {
            return null;
        }
        ExLabwareStatusEnum status = ExLabwareStatusEnum.fromCode(externalStatusCode);
        return switch (status) {
            case LS_New -> "AVAILABLE";
            case LS_Using, LS_Harverst -> "IN_USE";
            case LS_Discard -> "DISCARDED";
            case LS_Used -> "USED";
        };
    }

    static MaterialItem toMaterialItem(MaterialInstanceDO instance,
                                       MaterialInstanceDO parent,
                                       SlotDO rootSlot,
                                       ContainerTypeDO containerType,
                                       SampleInfoDO sample) {
        MaterialItem item = new MaterialItem();
        item.setId(instance.getId());
        item.setCode(String.valueOf(instance.getId()));

        ExLabwareTypeEnum labwareType = resolveLabwareType(containerType);
        if (labwareType != null) {
            item.setType(labwareType.getCode());
        }

        item.setPosition(buildPosition(instance, parent, rootSlot));
        fillQuantityAndCapacity(item, instance, containerType, labwareType);
        item.setVolume(toDouble(instance.getCurrentVolUl()));
        item.setLiquid(resolveLiquidCode(instance.getContentDefCode()));
        item.setStatus(toExternalStatus(instance.getInstanceStatus()));
        item.setBatchId(resolveBatchId(instance, sample));
        return item;
    }

    static MaterialItem toCarrierMaterialItem(MaterialInstanceDO carrier,
                                              SlotDO rootSlot,
                                              ContainerTypeDO carrierType,
                                              List<MaterialInstanceDO> children,
                                              Map<Long, ContainerTypeDO> containerTypeById,
                                              Map<String, SampleInfoDO> sampleBySampleId) {
        MaterialItem item = toMaterialItem(carrier, null, rootSlot, carrierType, null);
        if (children == null || children.isEmpty()) {
            item.setChildLabware(List.of());
            return item;
        }
        List<MaterialItem> childItems = new ArrayList<>(children.size());
        for (MaterialInstanceDO child : children) {
            ContainerTypeDO childType = containerTypeById.get(child.getContainerTypeId());
            SampleInfoDO sample = StrUtil.isNotBlank(child.getSampleId())
                    ? sampleBySampleId.get(child.getSampleId()) : null;
            childItems.add(toMaterialItem(child, carrier, rootSlot, childType, sample));
        }
        item.setChildLabware(childItems);
        return item;
    }

    static boolean isCarrierBoard(ContainerTypeDO containerType) {
        if (containerType == null) {
            return false;
        }
        if ("CARRIER".equalsIgnoreCase(containerType.getHierarchyRole())) {
            return true;
        }
        return ExLabwareTypeEnum.LabwareType_50CentrifugeTube_Board.name().equals(containerType.getTypeCode());
    }

    private static ExLabwareTypeEnum resolveLabwareType(ContainerTypeDO containerType) {
        if (containerType == null || StrUtil.isBlank(containerType.getTypeCode())) {
            return null;
        }
        try {
            return ExLabwareTypeEnum.valueOf(containerType.getTypeCode());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String buildPosition(MaterialInstanceDO instance, MaterialInstanceDO parent, SlotDO rootSlot) {
        if (rootSlot == null) {
            return StrUtil.blankToDefault(instance.getRootSlotCode(), "");
        }
        String zoneOrSlot = StrUtil.blankToDefault(rootSlot.getZoneCode(), rootSlot.getSlotCode());
        if (parent != null && StrUtil.isNotBlank(instance.getParentPositionCode())) {
            return zoneOrSlot + "|" + instance.getParentPositionCode();
        }
        return zoneOrSlot;
    }

    private static void fillQuantityAndCapacity(MaterialItem item, MaterialInstanceDO instance,
                                                ContainerTypeDO containerType, ExLabwareTypeEnum labwareType) {
        if (labwareType == ExLabwareTypeEnum.LabwareType_10mlTip
                || labwareType == ExLabwareTypeEnum.LabwareType_1mlTip
                || labwareType == ExLabwareTypeEnum.LabwareType_6_Board
                || labwareType == ExLabwareTypeEnum.LabwareType_96_Board) {
            int qty = instance.getCurrentCount() != null ? instance.getCurrentCount()
                    : (containerType != null && containerType.getMaxCount() != null ? containerType.getMaxCount() : 0);
            item.setQuantity(qty);
            item.setCapacity(0.0);
            return;
        }
        item.setQuantity(0);
        double cap = 0.0;
        if (containerType != null && containerType.getMaxVolUl() != null) {
            cap = containerType.getMaxVolUl().doubleValue();
        }
        item.setCapacity(cap);
    }

    private static Integer resolveLiquidCode(String contentDefCode) {
        if (StrUtil.isBlank(contentDefCode)) {
            return ExLiquidTypeEnum.LT_None.getCode();
        }
        try {
            return ExLiquidTypeEnum.valueOf(contentDefCode).getCode();
        } catch (IllegalArgumentException e) {
            return ExLiquidTypeEnum.LT_None.getCode();
        }
    }

    private static int toExternalStatus(String instanceStatus) {
        if (StrUtil.isBlank(instanceStatus)) {
            return ExLabwareStatusEnum.LS_New.getCode();
        }
        return switch (instanceStatus) {
            case "AVAILABLE", "RESERVED" -> ExLabwareStatusEnum.LS_New.getCode();
            case "IN_USE" -> ExLabwareStatusEnum.LS_Using.getCode();
            case "DISCARDED", "EXPIRED" -> ExLabwareStatusEnum.LS_Discard.getCode();
            case "USED" -> ExLabwareStatusEnum.LS_Used.getCode();
            default -> ExLabwareStatusEnum.LS_New.getCode();
        };
    }

    private static int resolveBatchId(MaterialInstanceDO instance, SampleInfoDO sample) {
        if (sample != null && StrUtil.isNotBlank(sample.getSource())) {
            try {
                return Integer.parseInt(sample.getSource());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (StrUtil.isNotBlank(instance.getDescription())) {
            try {
                return Integer.parseInt(instance.getDescription());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0;
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    static java.util.HashMap<Long, MaterialInstanceDO> indexById(java.util.Collection<MaterialInstanceDO> instances) {
        java.util.HashMap<Long, MaterialInstanceDO> map = new java.util.HashMap<>();
        if (instances == null) {
            return map;
        }
        for (MaterialInstanceDO instance : instances) {
            if (instance.getId() != null) {
                map.put(instance.getId(), instance);
            }
        }
        return map;
    }
}

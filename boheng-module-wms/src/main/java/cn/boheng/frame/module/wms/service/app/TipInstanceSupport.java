package cn.boheng.frame.module.wms.service.app;

import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.enums.external.ExLabwareTypeEnum;

/**
 * Tip 盒实例判定与数量字段
 */
final class TipInstanceSupport {

    private TipInstanceSupport() {
    }

    static boolean isTipContainer(ContainerTypeDO containerType) {
        if (containerType == null) {
            return false;
        }
        if ("TIP".equalsIgnoreCase(containerType.getCategory())) {
            return true;
        }
        String code = containerType.getTypeCode();
        return ExLabwareTypeEnum.LabwareType_10mlTip.name().equals(code)
                || ExLabwareTypeEnum.LabwareType_1mlTip.name().equals(code);
    }

    static int currentCount(MaterialInstanceDO instance) {
        return instance.getCurrentCount() != null ? instance.getCurrentCount() : 0;
    }

    static int maxCount(MaterialInstanceDO instance, ContainerTypeDO containerType) {
        if (containerType != null && containerType.getMaxCount() != null) {
            return containerType.getMaxCount();
        }
        return currentCount(instance);
    }
}

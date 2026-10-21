package cn.boheng.frame.module.wms.service.app;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 槽位 {@code suitable_materials} 与外部器具类型匹配（对应 qinghua {@code suitableResourceType}）
 */
final class SlotSuitableMaterialMatcher {

    private SlotSuitableMaterialMatcher() {
    }

    /**
     * 槽位是否允许放置指定容器类型编码（ExLabwareTypeEnum.name() / container_type.type_code）
     */
    static boolean supportsContainerType(String suitableMaterialsJson, String containerTypeCode) {
        if (StrUtil.isBlank(containerTypeCode) || StrUtil.isBlank(suitableMaterialsJson)) {
            return false;
        }
        JSONArray array;
        try {
            array = JSONUtil.parseArray(suitableMaterialsJson);
        } catch (Exception e) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            if (!"CONTAINER".equalsIgnoreCase(item.getStr("kind"))) {
                continue;
            }
            if (containerTypeCode.equals(item.getStr("materialCode"))) {
                return true;
            }
        }
        return false;
    }
}

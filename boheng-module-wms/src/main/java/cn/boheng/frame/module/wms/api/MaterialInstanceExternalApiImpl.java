package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.util.LabwareCodeConvertUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link MaterialInstanceExternalApi} 实现。
 */
@Service
public class MaterialInstanceExternalApiImpl implements MaterialInstanceExternalApi {

    private static final String STATUS_IN_USE = "IN_USE";

    @Resource
    private MaterialInstanceMapper materialInstanceMapper;

    @Override
    public List<Integer> listEncodedUsingLabwareIdsBySampleCodes(Collection<String> sampleCodes) {
        if (CollUtil.isEmpty(sampleCodes)) {
            return List.of();
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (String sampleCode : sampleCodes) {
            if (StrUtil.isBlank(sampleCode)) {
                continue;
            }
            List<MaterialInstanceDO> instances = materialInstanceMapper.selectListBySampleId(sampleCode.trim());
            for (MaterialInstanceDO instance : instances) {
                if (!STATUS_IN_USE.equals(instance.getInstanceStatus())) {
                    continue;
                }
                ids.add(LabwareCodeConvertUtil.encodeMaterialInstance(
                        instance.getInstanceCode(), instance.getId()));
            }
        }
        return new ArrayList<>(ids);
    }

    @Override
    public Integer findFirstEncodedLabwareId(String sampleOrBatchKey, String liquidTypeName) {
        if (StrUtil.isBlank(sampleOrBatchKey) || StrUtil.isBlank(liquidTypeName)) {
            return null;
        }
        List<MaterialInstanceDO> instances = materialInstanceMapper.selectListBySampleId(sampleOrBatchKey.trim());
        for (MaterialInstanceDO instance : instances) {
            if (!liquidTypeName.equals(instance.getContentDefCode())) {
                continue;
            }
            if (STATUS_IN_USE.equals(instance.getInstanceStatus())
                    || "AVAILABLE".equals(instance.getInstanceStatus())) {
                return LabwareCodeConvertUtil.encodeMaterialInstance(
                        instance.getInstanceCode(), instance.getId());
            }
        }
        return null;
    }

}

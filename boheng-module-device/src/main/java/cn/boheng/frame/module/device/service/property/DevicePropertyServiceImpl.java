package cn.boheng.frame.module.device.service.property;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertyPageReqVO;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertySaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.property.DevicePropertyDO;
import cn.boheng.frame.module.device.dal.mysql.property.DevicePropertyMapper;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.*;

/**
 * 设备可观测属性定义 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class DevicePropertyServiceImpl implements DevicePropertyService {

    @Resource
    private DevicePropertyMapper devicePropertyMapper;

    @Override
    public Long createProperty(DevicePropertySaveReqVO createReqVO) {
        // 校验「类型 + 属性」唯一性
        validatePropertyUnique(createReqVO.getId(), createReqVO.getDeviceTypeCode(), createReqVO.getPropertyCode());

        // 插入
        DevicePropertyDO property = BeanUtils.toBean(createReqVO, DevicePropertyDO.class);
        devicePropertyMapper.insert(property);
        return property.getId();
    }

    @Override
    public void updateProperty(DevicePropertySaveReqVO updateReqVO) {
        // 校验存在
        validatePropertyExists(updateReqVO.getId());
        // 校验「类型 + 属性」唯一性
        validatePropertyUnique(updateReqVO.getId(), updateReqVO.getDeviceTypeCode(), updateReqVO.getPropertyCode());

        // 更新
        DevicePropertyDO updateObj = BeanUtils.toBean(updateReqVO, DevicePropertyDO.class);
        devicePropertyMapper.updateById(updateObj);
    }

    @Override
    public void deleteProperty(Long id) {
        validatePropertyExists(id);
        devicePropertyMapper.deleteById(id);
    }

    @Override
    public void deletePropertyList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        devicePropertyMapper.deleteBatch(DevicePropertyDO::getId, ids);
    }

    @Override
    public DevicePropertyDO getProperty(Long id) {
        return devicePropertyMapper.selectById(id);
    }

    @Override
    public PageResult<DevicePropertyDO> getPropertyPage(DevicePropertyPageReqVO pageReqVO) {
        return devicePropertyMapper.selectPage(pageReqVO);
    }

    @Override
    public List<DevicePropertyDO> getPropertyList(String deviceTypeCode) {
        if (StrUtil.isBlank(deviceTypeCode)) {
            return devicePropertyMapper.selectList();
        }
        return devicePropertyMapper.selectListByTypeCode(deviceTypeCode);
    }

    @VisibleForTesting
    DevicePropertyDO validatePropertyExists(Long id) {
        DevicePropertyDO property = devicePropertyMapper.selectById(id);
        if (property == null) {
            throw exception(DEVICE_PROPERTY_NOT_EXISTS);
        }
        return property;
    }

    @VisibleForTesting
    void validatePropertyUnique(Long id, String deviceTypeCode, String propertyCode) {
        if (StrUtil.hasBlank(deviceTypeCode, propertyCode)) {
            return;
        }
        DevicePropertyDO property = devicePropertyMapper.selectByTypeAndProperty(deviceTypeCode, propertyCode);
        if (property == null) {
            return;
        }
        if (id == null || !property.getId().equals(id)) {
            throw exception(DEVICE_PROPERTY_CODE_DUPLICATE, deviceTypeCode, propertyCode);
        }
    }

}

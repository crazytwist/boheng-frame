package cn.boheng.frame.module.device.service.device;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
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
 * 设备台账 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class DeviceInfoServiceImpl implements DeviceInfoService {

    @Resource
    private DeviceInfoMapper deviceInfoMapper;

    @Override
    public Long createDevice(DeviceSaveReqVO createReqVO) {
        // 校验设备编码唯一性
        validateDeviceCodeUnique(createReqVO.getId(), createReqVO.getDeviceCode());

        // 插入
        DeviceInfoDO device = BeanUtils.toBean(createReqVO, DeviceInfoDO.class);
        deviceInfoMapper.insert(device);
        return device.getId();
    }

    @Override
    public void updateDevice(DeviceSaveReqVO updateReqVO) {
        // 校验存在
        validateDeviceExists(updateReqVO.getId());
        // 校验设备编码唯一性
        validateDeviceCodeUnique(updateReqVO.getId(), updateReqVO.getDeviceCode());

        // 更新
        DeviceInfoDO updateObj = BeanUtils.toBean(updateReqVO, DeviceInfoDO.class);
        deviceInfoMapper.updateById(updateObj);
    }

    @Override
    public void deleteDevice(Long id) {
        // 校验存在
        validateDeviceExists(id);
        // 删除
        deviceInfoMapper.deleteById(id);
    }

    @Override
    public void deleteDeviceList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        deviceInfoMapper.deleteBatch(DeviceInfoDO::getId, ids);
    }

    @Override
    public DeviceInfoDO getDevice(Long id) {
        return deviceInfoMapper.selectById(id);
    }

    @Override
    public PageResult<DeviceInfoDO> getDevicePage(DevicePageReqVO pageReqVO) {
        return deviceInfoMapper.selectPage(pageReqVO);
    }

    @Override
    public List<DeviceInfoDO> getDeviceList() {
        return deviceInfoMapper.selectList();
    }

    @VisibleForTesting
    DeviceInfoDO validateDeviceExists(Long id) {
        DeviceInfoDO device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        return device;
    }

    @VisibleForTesting
    void validateDeviceCodeUnique(Long id, String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            return;
        }
        DeviceInfoDO device = deviceInfoMapper.selectByDeviceCode(deviceCode);
        if (device == null) {
            return;
        }
        if (id == null || !device.getId().equals(id)) {
            throw exception(DEVICE_CODE_DUPLICATE, deviceCode);
        }
    }

}

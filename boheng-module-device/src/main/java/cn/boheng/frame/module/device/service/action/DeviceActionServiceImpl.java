package cn.boheng.frame.module.device.service.action;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionPageReqVO;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
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
 * 设备动作定义 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class DeviceActionServiceImpl implements DeviceActionService {

    @Resource
    private DeviceActionMapper deviceActionMapper;

    @Override
    public Long createAction(DeviceActionSaveReqVO createReqVO) {
        // 校验「类型 + 动作」唯一性
        validateActionUnique(createReqVO.getId(), createReqVO.getDeviceTypeCode(), createReqVO.getActionCode());

        // 插入
        DeviceActionDO action = BeanUtils.toBean(createReqVO, DeviceActionDO.class);
        deviceActionMapper.insert(action);
        return action.getId();
    }

    @Override
    public void updateAction(DeviceActionSaveReqVO updateReqVO) {
        // 校验存在
        validateActionExists(updateReqVO.getId());
        // 校验「类型 + 动作」唯一性
        validateActionUnique(updateReqVO.getId(), updateReqVO.getDeviceTypeCode(), updateReqVO.getActionCode());

        // 更新
        DeviceActionDO updateObj = BeanUtils.toBean(updateReqVO, DeviceActionDO.class);
        deviceActionMapper.updateById(updateObj);
    }

    @Override
    public void deleteAction(Long id) {
        validateActionExists(id);
        deviceActionMapper.deleteById(id);
    }

    @Override
    public void deleteActionList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        deviceActionMapper.deleteBatch(DeviceActionDO::getId, ids);
    }

    @Override
    public DeviceActionDO getAction(Long id) {
        return deviceActionMapper.selectById(id);
    }

    @Override
    public PageResult<DeviceActionDO> getActionPage(DeviceActionPageReqVO pageReqVO) {
        return deviceActionMapper.selectPage(pageReqVO);
    }

    @Override
    public List<DeviceActionDO> getActionList(String deviceTypeCode) {
        if (StrUtil.isBlank(deviceTypeCode)) {
            return deviceActionMapper.selectList();
        }
        return deviceActionMapper.selectListByTypeCode(deviceTypeCode);
    }

    @VisibleForTesting
    DeviceActionDO validateActionExists(Long id) {
        DeviceActionDO action = deviceActionMapper.selectById(id);
        if (action == null) {
            throw exception(DEVICE_ACTION_NOT_EXISTS);
        }
        return action;
    }

    @VisibleForTesting
    void validateActionUnique(Long id, String deviceTypeCode, String actionCode) {
        if (StrUtil.hasBlank(deviceTypeCode, actionCode)) {
            return;
        }
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(deviceTypeCode, actionCode);
        if (action == null) {
            return;
        }
        if (id == null || !action.getId().equals(id)) {
            throw exception(DEVICE_ACTION_CODE_DUPLICATE, deviceTypeCode, actionCode);
        }
    }

}

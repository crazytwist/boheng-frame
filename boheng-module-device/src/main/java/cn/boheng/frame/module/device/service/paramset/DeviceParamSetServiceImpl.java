package cn.boheng.frame.module.device.service.paramset;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetPageReqVO;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.paramset.DeviceParamSetDO;
import cn.boheng.frame.module.device.dal.mysql.paramset.DeviceParamSetMapper;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.*;

/**
 * 参数集预设 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class DeviceParamSetServiceImpl implements DeviceParamSetService {
    /**
     * 参数集表
     */

    @Resource
    private DeviceParamSetMapper deviceParamSetMapper;
    /**
     * 创建参数集
     */

    @Override
    public Long createParamSet(DeviceParamSetSaveReqVO createReqVO) {
        // 校验参数集编码唯一性
        validateParamSetCodeUnique(createReqVO.getId(), createReqVO.getParamSetCode());

        // 插入
        DeviceParamSetDO paramSet = BeanUtils.toBean(createReqVO, DeviceParamSetDO.class);
        deviceParamSetMapper.insert(paramSet);
        return paramSet.getId();
    }
    /**
     * 更新参数集
     */

    @Override
    public void updateParamSet(DeviceParamSetSaveReqVO updateReqVO) {
        // 校验存在
        validateParamSetExists(updateReqVO.getId());
        // 校验参数集编码唯一性
        validateParamSetCodeUnique(updateReqVO.getId(), updateReqVO.getParamSetCode());

        // 更新
        DeviceParamSetDO updateObj = BeanUtils.toBean(updateReqVO, DeviceParamSetDO.class);
        deviceParamSetMapper.updateById(updateObj);
    }
    /**
     * 删除参数集
     */

    @Override
    public void deleteParamSet(Long id) {
        validateParamSetExists(id);
        deviceParamSetMapper.deleteById(id);
    }
    /**
     * 批量删除参数集
     */

    @Override
    public void deleteParamSetList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        deviceParamSetMapper.deleteBatch(DeviceParamSetDO::getId, ids);
    }
    /**
     * 获得参数集
     */

    @Override
    public DeviceParamSetDO getParamSet(Long id) {
        return deviceParamSetMapper.selectById(id);
    }
    /**
     * 获得参数集分页
     */

    @Override
    public PageResult<DeviceParamSetDO> getParamSetPage(DeviceParamSetPageReqVO pageReqVO) {
        return deviceParamSetMapper.selectPage(pageReqVO);
    }
    /**
     * 获得参数集列表，可按设备类型和动作过滤
     */

    @Override
    public List<DeviceParamSetDO> getParamSetList(String deviceTypeCode, String actionCode) {
        if (StrUtil.hasBlank(deviceTypeCode, actionCode)) {
            return deviceParamSetMapper.selectList();
        }
        return deviceParamSetMapper.selectListByTypeAndAction(deviceTypeCode, actionCode);
    }
    /**
     * 标记参数集是否已验证
     */

    @Override
    public void updateValidated(Long id, Boolean validated, String validatedBy) {
        // 校验存在
        validateParamSetExists(id);
        // 只更新验证相关三字段，避免覆盖其他并发编辑
        DeviceParamSetDO updateObj = new DeviceParamSetDO();
        updateObj.setId(id);
        updateObj.setValidated(validated);
        updateObj.setValidatedBy(Boolean.TRUE.equals(validated) ? validatedBy : null);
        updateObj.setValidatedTime(Boolean.TRUE.equals(validated) ? LocalDateTime.now() : null);
        deviceParamSetMapper.updateById(updateObj);
    }
    /**
     * 校验参数集存在
     */

    @VisibleForTesting
    DeviceParamSetDO validateParamSetExists(Long id) {
        DeviceParamSetDO paramSet = deviceParamSetMapper.selectById(id);
        if (paramSet == null) {
            throw exception(DEVICE_PARAM_SET_NOT_EXISTS);
        }
        return paramSet;
    }
    /**
     * 校验参数集编码不重复
     */

    @VisibleForTesting
    void validateParamSetCodeUnique(Long id, String paramSetCode) {
        if (StrUtil.isBlank(paramSetCode)) {
            return;
        }
        DeviceParamSetDO paramSet = deviceParamSetMapper.selectByParamSetCode(paramSetCode);
        if (paramSet == null) {
            return;
        }
        if (id == null || !paramSet.getId().equals(id)) {
            throw exception(DEVICE_PARAM_SET_CODE_DUPLICATE, paramSetCode);
        }
    }

}

package cn.boheng.frame.module.device.service.paramset;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetPageReqVO;
import cn.boheng.frame.module.device.controller.admin.paramset.vo.DeviceParamSetSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.paramset.DeviceParamSetDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.paramset.DeviceParamSetMapper;
import cn.boheng.frame.module.device.service.action.DeviceParamResolver;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * 设备动作表，用来取得参数集对应的输入契约
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;
    /**
     * 设备动作输入契约解析器
     */
    @Resource
    private DeviceParamResolver deviceParamResolver;
    /**
     * 创建参数集
     */

    @Override
    public Long createParamSet(DeviceParamSetSaveReqVO createReqVO) {
        // 校验参数集编码唯一性
        validateParamSetCodeUnique(createReqVO.getId(), createReqVO.getParamSetCode());
        String paramsJson = validateParams(createReqVO.getDeviceTypeCode(), createReqVO.getActionCode(),
                paramsJsonOf(createReqVO.getParamsJson()));

        // 插入
        DeviceParamSetDO paramSet = buildParamSet(createReqVO, paramsJson);
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
        String paramsJson = validateParams(updateReqVO.getDeviceTypeCode(), updateReqVO.getActionCode(),
                paramsJsonOf(updateReqVO.getParamsJson()));

        // 更新
        DeviceParamSetDO updateObj = buildParamSet(updateReqVO, paramsJson);
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
        DeviceParamSetDO paramSet = validateParamSetExists(id);
        if (Boolean.TRUE.equals(validated)) {
            validateParams(paramSet.getDeviceTypeCode(), paramSet.getActionCode(), paramSet.getParamsJson());
        }
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

    /**
     * 根据关联动作的输入契约校验参数集，并展开契约默认值。
     */
    private String validateParams(String deviceTypeCode, String actionCode, String paramsJson) {
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(deviceTypeCode, actionCode);
        if (action == null) {
            throw exception(DEVICE_PARAM_SET_ACTION_NOT_EXISTS, deviceTypeCode, actionCode);
        }
        return deviceParamResolver.resolve(action.getParamSchema(), paramsJson);
    }

    /**
     * 将接口参数对象原样序列化为持久化 JSON。
     */
    private String paramsJsonOf(Map<String, Object> paramsJson) {
        return paramsJson == null ? null : JSONUtil.toJsonStr(paramsJson);
    }

    /**
     * 将保存请求转换为参数集持久化对象，明确处理 Map 与 JSON 字符串的边界。
     */
    private DeviceParamSetDO buildParamSet(DeviceParamSetSaveReqVO reqVO, String paramsJson) {
        DeviceParamSetDO paramSet = new DeviceParamSetDO();
        paramSet.setId(reqVO.getId());
        paramSet.setParamSetCode(reqVO.getParamSetCode());
        paramSet.setParamSetName(reqVO.getParamSetName());
        paramSet.setDeviceTypeCode(reqVO.getDeviceTypeCode());
        paramSet.setActionCode(reqVO.getActionCode());
        paramSet.setParamsJson(paramsJson);
        paramSet.setValidated(reqVO.getValidated());
        paramSet.setValidatedBy(reqVO.getValidatedBy());
        paramSet.setStatus(reqVO.getStatus());
        return paramSet;
    }

}

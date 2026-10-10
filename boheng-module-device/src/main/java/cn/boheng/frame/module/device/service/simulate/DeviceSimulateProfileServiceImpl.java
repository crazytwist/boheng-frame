package cn.boheng.frame.module.device.service.simulate;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfilePageReqVO;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfileSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.simulate.DeviceSimulateProfileMapper;
import cn.boheng.frame.module.device.enums.SimulateReturnModeEnum;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import java.util.Set;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_SIMULATE_INVALID;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_SIMULATE_PROFILE_DUPLICATE;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_SIMULATE_PROFILE_NOT_EXISTS;

/**
 * 仿真 HTTP 配置的维护实现
 */
@Service
@Validated
public class DeviceSimulateProfileServiceImpl implements DeviceSimulateProfileService {

    /**
     * 允许的回收方式
     */
    private static final Set<String> MODES = Set.of(
            SimulateReturnModeEnum.SYNC.name(),
            SimulateReturnModeEnum.CALLBACK.name(),
            SimulateReturnModeEnum.POLL.name());

    /**
     * 一次轮询调用最长占用的时间。调用方的 HTTP 要等这么久，超出后前端会先超时
     */
    private static final long MAX_POLL_TOTAL_MS = 10 * 60 * 1000L;

    /**
     * 仿真配置表
     */
    @Resource
    private DeviceSimulateProfileMapper profileMapper;

    /**
     * 设备台账，用来核对设备存在并冗余设备编码
     */
    @Resource
    private DeviceInfoService deviceInfoService;

    /**
     * 设备动作，仿真配置必须指向已有动作
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;

    /**
     * 创建一条仿真配置
     */
    @Override
    public Long createProfile(DeviceSimulateProfileSaveReqVO createReqVO) {
        DeviceInfoDO device = normalize(createReqVO);
        validateUnique(null, device.getId(), createReqVO.getActionCode(), device.getDeviceCode());
        DeviceSimulateProfileDO row = BeanUtils.toBean(createReqVO, DeviceSimulateProfileDO.class);
        row.setDeviceCode(device.getDeviceCode());
        profileMapper.insert(row);
        return row.getId();
    }

    /**
     * 修改一条仿真配置
     */
    @Override
    public void updateProfile(DeviceSimulateProfileSaveReqVO updateReqVO) {
        if (updateReqVO.getId() == null || profileMapper.selectById(updateReqVO.getId()) == null) {
            throw exception(DEVICE_SIMULATE_PROFILE_NOT_EXISTS);
        }
        DeviceInfoDO device = normalize(updateReqVO);
        validateUnique(updateReqVO.getId(), device.getId(), updateReqVO.getActionCode(), device.getDeviceCode());
        DeviceSimulateProfileDO row = BeanUtils.toBean(updateReqVO, DeviceSimulateProfileDO.class);
        row.setDeviceCode(device.getDeviceCode());
        profileMapper.updateById(row);
    }

    /**
     * 删除一条仿真配置
     */
    @Override
    public void deleteProfile(Long id) {
        if (profileMapper.selectById(id) == null) {
            throw exception(DEVICE_SIMULATE_PROFILE_NOT_EXISTS);
        }
        profileMapper.deleteById(id);
    }

    /**
     * 获得一条仿真配置
     */
    @Override
    public DeviceSimulateProfileDO getProfile(Long id) {
        return profileMapper.selectById(id);
    }

    /**
     * 分页查询仿真配置
     */
    @Override
    public PageResult<DeviceSimulateProfileDO> getProfilePage(DeviceSimulateProfilePageReqVO pageReqVO) {
        return profileMapper.selectPage(pageReqVO);
    }

    /**
     * 取某台设备某个动作上启用的配置。没有或已停用时返回 null。
     */
    @Override
    public DeviceSimulateProfileDO getEnabled(Long deviceId, String actionCode) {
        DeviceSimulateProfileDO profile = profileMapper.selectByDeviceAndAction(deviceId, actionCode);
        if (profile == null || (profile.getStatus() != null && profile.getStatus() != 0)) {
            return null;
        }
        return profile;
    }

    /**
     * 校验设备、动作和数值范围，并把空值补成默认
     */
    private DeviceInfoDO normalize(DeviceSimulateProfileSaveReqVO reqVO) {
        DeviceInfoDO device = deviceInfoService.getDevice(reqVO.getDeviceId());
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        reqVO.setActionCode(StrUtil.trim(reqVO.getActionCode()));
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(device.getDeviceTypeCode(), reqVO.getActionCode());
        if (action == null) {
            throw exception(DEVICE_ACTION_NOT_EXISTS);
        }
        SimulateReturnModeEnum parsed = SimulateReturnModeEnum.of(
                StrUtil.blankToDefault(reqVO.getReturnMode(), SimulateReturnModeEnum.SYNC.name()));
        String mode = parsed == null ? "" : parsed.name();
        if (!MODES.contains(mode)) {
            throw exception(DEVICE_SIMULATE_INVALID, "回收方式只能是 SYNC、CALLBACK、POLL");
        }
        reqVO.setReturnMode(mode);
        if (reqVO.getAsyncWaitMs() == null) {
            reqVO.setAsyncWaitMs(0);
        }
        int maxWait = parsed == SimulateReturnModeEnum.CALLBACK
                ? SimulateHttpDeviceService.MAX_CALLBACK_DELAY_MS : SimulateHttpDeviceService.MAX_WAIT_MS;
        if (reqVO.getAsyncWaitMs() < 0 || reqVO.getAsyncWaitMs() > maxWait) {
            throw exception(DEVICE_SIMULATE_INVALID, "等待时间需在 0 到 " + maxWait + " 毫秒之间");
        }
        if (reqVO.getPollIntervalMs() == null) {
            reqVO.setPollIntervalMs(1000);
        }
        if (reqVO.getPollMaxTimes() == null) {
            reqVO.setPollMaxTimes(30);
        }
        if (SimulateReturnModeEnum.POLL.name().equals(mode)) {
            if (reqVO.getPollIntervalMs() < 100 || reqVO.getPollIntervalMs() > 60_000) {
                throw exception(DEVICE_SIMULATE_INVALID, "轮询间隔需在 100 到 60000 毫秒之间");
            }
            if (reqVO.getPollMaxTimes() < 1 || reqVO.getPollMaxTimes() > 200) {
                throw exception(DEVICE_SIMULATE_INVALID, "最大轮询次数需在 1 到 200 之间");
            }
            if ((long) reqVO.getPollIntervalMs() * reqVO.getPollMaxTimes() > MAX_POLL_TOTAL_MS) {
                throw exception(DEVICE_SIMULATE_INVALID, "轮询间隔 × 最多轮询次数不能超过 10 分钟");
            }
        }
        if (reqVO.getStatus() == null) {
            reqVO.setStatus(0);
        }
        reqVO.setRequestTemplate(StrUtil.trimToNull(reqVO.getRequestTemplate()));
        reqVO.setResponseBody(StrUtil.trimToNull(reqVO.getResponseBody()));
        reqVO.setAckBody(StrUtil.trimToNull(reqVO.getAckBody()));
        reqVO.setPendingBody(StrUtil.trimToNull(reqVO.getPendingBody()));
        return device;
    }

    /**
     * 同一设备的同一动作只允许一条配置
     */
    private void validateUnique(Long id, Long deviceId, String actionCode, String deviceCode) {
        DeviceSimulateProfileDO existing = profileMapper.selectByDeviceAndAction(deviceId, actionCode);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(DEVICE_SIMULATE_PROFILE_DUPLICATE, deviceCode, actionCode);
        }
    }

}

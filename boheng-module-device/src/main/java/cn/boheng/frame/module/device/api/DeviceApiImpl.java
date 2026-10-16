package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceSelectReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备域对外 API 实现
 *
 * 实现契约层 {@link DeviceApi}，供其它模块（调度域等）跨模块调用。
 *
 * @author yinan
 */
@Service
@Validated
public class DeviceApiImpl implements DeviceApi {
    /**
     * 设备台账表
     */

    @Resource
    private DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备动作表。发布流程时用来确认动作仍启用
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;
    /**
     * 按设备类型挑选在线且空闲的设备
     */

    @Override
    public List<DeviceSummaryDTO> selectDevices(DeviceSelectReqDTO reqVO) {
        // 一期按设备类型查询，再做「在线 + 空闲」过滤与排序
        List<DeviceInfoDO> devices = deviceInfoMapper.selectListByTypeCode(reqVO.getDeviceTypeCode());
        List<DeviceSummaryDTO> result = new ArrayList<>();
        for (DeviceInfoDO device : devices) {
            // 在线过滤
            if (Boolean.TRUE.equals(reqVO.getRequireOnline()) && !"ONLINE".equals(device.getStatus())) {
                continue;
            }
            // 空闲过滤：无占用锁 且 锁未过期
            boolean idle = isIdle(device);
            if (Boolean.TRUE.equals(reqVO.getRequireIdle()) && !idle) {
                continue;
            }
            DeviceSummaryDTO summary = new DeviceSummaryDTO();
            summary.setId(device.getId());
            summary.setDeviceCode(device.getDeviceCode());
            summary.setDeviceName(device.getDeviceName());
            summary.setDeviceTypeCode(device.getDeviceTypeCode());
            summary.setStatus(device.getStatus());
            summary.setIdle(idle);
            result.add(summary);
        }
        // 截取数量上限（默认 1）
        int limit = reqVO.getLimit() == null ? 1 : reqVO.getLimit();
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /**
     * 按编码取设备摘要。不存在时返回 null，不抛业务异常，交给调用方决定怎么提示。
     */
    @Override
    public DeviceSummaryDTO getDeviceByCode(String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            return null;
        }
        DeviceInfoDO device = deviceInfoMapper.selectByDeviceCode(deviceCode.trim());
        if (device == null) {
            return null;
        }
        DeviceSummaryDTO summary = toSummary(device);
        summary.setIdle(isIdle(device));
        return summary;
    }

    /**
     * 类型下存在该动作，且状态为空或 0（启用）时返回 true。停用动作不能进已发布流程。
     */
    @Override
    public boolean hasEnabledAction(String deviceTypeCode, String actionCode) {
        if (StrUtil.hasBlank(deviceTypeCode, actionCode)) {
            return false;
        }
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(deviceTypeCode.trim(), actionCode.trim());
        return action != null && (action.getStatus() == null || action.getStatus() == 0);
    }

    /**
     * 设备能力集：设备编码 -> 类型 -> 启用动作编码列表。任务队列按能力路由用。
     * 设备不存在、没有动作、或动作全停用时返回空列表，不抛业务异常。
     */
    @Override
    public List<String> listActionCodesByDevice(String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            return List.of();
        }
        DeviceInfoDO device = deviceInfoMapper.selectByDeviceCode(deviceCode.trim());
        if (device == null) {
            return List.of();
        }
        List<DeviceActionDO> actions = deviceActionMapper.selectListByTypeCode(device.getDeviceTypeCode());
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        return actions.stream()
                .filter(action -> action.getStatus() == null || action.getStatus() == 0)
                .map(DeviceActionDO::getActionCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    /**
     * 把台账行收成跨模块摘要，不带接入地址和登录信息。
     */
    private DeviceSummaryDTO toSummary(DeviceInfoDO device) {
        DeviceSummaryDTO summary = new DeviceSummaryDTO();
        summary.setId(device.getId());
        summary.setDeviceCode(device.getDeviceCode());
        summary.setDeviceName(device.getDeviceName());
        summary.setDeviceTypeCode(device.getDeviceTypeCode());
        summary.setStatus(device.getStatus());
        return summary;
    }

    /**
     * 判断设备是否空闲：无持有方 或 锁已过期（锁必须能过期，否则设备永久锁死）。
     */
    private boolean isIdle(DeviceInfoDO device) {
        if (device.getLockHolder() == null && device.getLockType() == null) {
            return true;
        }
        LocalDateTime expireTime = device.getLockExpireTime();
        return expireTime != null && expireTime.isBefore(LocalDateTime.now());
    }

}

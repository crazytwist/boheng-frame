package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceSelectReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
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

package cn.boheng.frame.module.device.service.simulate;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfilePageReqVO;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfileSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;

/**
 * 仿真 HTTP 配置的维护
 */
public interface DeviceSimulateProfileService {

    /**
     * 创建一条仿真配置
     */
    Long createProfile(DeviceSimulateProfileSaveReqVO createReqVO);

    /**
     * 修改一条仿真配置
     */
    void updateProfile(DeviceSimulateProfileSaveReqVO updateReqVO);

    /**
     * 删除一条仿真配置
     */
    void deleteProfile(Long id);

    /**
     * 获得一条仿真配置
     */
    DeviceSimulateProfileDO getProfile(Long id);

    /**
     * 分页查询仿真配置
     */
    PageResult<DeviceSimulateProfileDO> getProfilePage(DeviceSimulateProfilePageReqVO pageReqVO);

    /**
     * 取某台设备某个动作上启用的配置。没有或已停用时返回 null。
     */
    DeviceSimulateProfileDO getEnabled(Long deviceId, String actionCode);

}

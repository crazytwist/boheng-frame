package cn.boheng.frame.module.device.service.device;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 设备台账 Service 接口
 *
 * @author yinan
 */
public interface DeviceInfoService {

    /**
     * 创建设备台账
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createDevice(@Valid DeviceSaveReqVO createReqVO);

    /**
     * 更新设备台账
     *
     * @param updateReqVO 更新信息
     */
    void updateDevice(@Valid DeviceSaveReqVO updateReqVO);

    /**
     * 删除设备台账
     *
     * @param id 编号
     */
    void deleteDevice(Long id);

    /**
     * 批量删除设备台账
     *
     * @param ids 编号列表
     */
    void deleteDeviceList(List<Long> ids);

    /**
     * 获得设备台账
     *
     * @param id 编号
     * @return 设备台账
     */
    DeviceInfoDO getDevice(Long id);

    /**
     * 获得设备台账分页
     *
     * @param pageReqVO 分页查询
     * @return 设备台账分页
     */
    PageResult<DeviceInfoDO> getDevicePage(DevicePageReqVO pageReqVO);

    /**
     * 获得设备台账列表
     *
     * @return 设备台账列表
     */
    List<DeviceInfoDO> getDeviceList();

}

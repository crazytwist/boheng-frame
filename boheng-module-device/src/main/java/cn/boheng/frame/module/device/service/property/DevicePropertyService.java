package cn.boheng.frame.module.device.service.property;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertyPageReqVO;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertySaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.property.DevicePropertyDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 设备可观测属性定义 Service 接口
 *
 * @author yinan
 */
public interface DevicePropertyService {

    /**
     * 创建设备属性
     *
     * @return 编号
     */
    Long createProperty(@Valid DevicePropertySaveReqVO createReqVO);

    /**
     * 更新设备属性
     */
    void updateProperty(@Valid DevicePropertySaveReqVO updateReqVO);

    /**
     * 删除设备属性
     */
    void deleteProperty(Long id);

    /**
     * 批量删除设备属性
     */
    void deletePropertyList(List<Long> ids);

    /**
     * 获得设备属性
     */
    DevicePropertyDO getProperty(Long id);

    /**
     * 获得设备属性分页
     */
    PageResult<DevicePropertyDO> getPropertyPage(DevicePropertyPageReqVO pageReqVO);

    /**
     * 获得设备属性列表（可按类型过滤）
     */
    List<DevicePropertyDO> getPropertyList(String deviceTypeCode);

}

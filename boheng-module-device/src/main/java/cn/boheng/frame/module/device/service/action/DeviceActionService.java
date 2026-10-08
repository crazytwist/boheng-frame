package cn.boheng.frame.module.device.service.action;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionPageReqVO;
import cn.boheng.frame.module.device.controller.admin.action.vo.DeviceActionSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 设备动作定义 Service 接口
 *
 * @author yinan
 */
public interface DeviceActionService {

    /**
     * 创建设备动作
     *
     * @return 编号
     */
    Long createAction(@Valid DeviceActionSaveReqVO createReqVO);

    /**
     * 更新设备动作
     */
    void updateAction(@Valid DeviceActionSaveReqVO updateReqVO);

    /**
     * 删除设备动作
     */
    void deleteAction(Long id);

    /**
     * 批量删除设备动作
     */
    void deleteActionList(List<Long> ids);

    /**
     * 获得设备动作
     */
    DeviceActionDO getAction(Long id);

    /**
     * 获得设备动作分页
     */
    PageResult<DeviceActionDO> getActionPage(DeviceActionPageReqVO pageReqVO);

    /**
     * 获得设备动作列表（可按类型过滤）
     */
    List<DeviceActionDO> getActionList(String deviceTypeCode);

}

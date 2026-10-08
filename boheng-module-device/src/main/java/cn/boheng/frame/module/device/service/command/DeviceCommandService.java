package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;

public interface DeviceCommandService {

    DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode);

    void complete(Long commandId, DeviceInvokeRespVO resp, String codecCode);

    void fail(Long commandId, String message);

    PageResult<DeviceCommandRespVO> getCommandPage(DeviceCommandPageReqVO pageReqVO);

    DeviceCommandRespVO getCommand(Long id);

}

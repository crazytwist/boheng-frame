package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;

/**
 * 命令记录服务。测试调用从这里落库
 */
public interface DeviceCommandService {

    /**
     * 调用开始时落一条已占用的命令
     */
    DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode);

    /**
     * 写入请求、响应和原始数据；配了解析规则时再生成测量
     */
    void complete(Long commandId, DeviceInvokeRespVO resp, String codecCode);

    /**
     * 调用没有完成时把命令标成失败
     */
    void fail(Long commandId, String message);

    /**
     * 命令记录分页
     */
    PageResult<DeviceCommandRespVO> getCommandPage(DeviceCommandPageReqVO pageReqVO);

    /**
     * 命令详情，带上原始响应正文
     */
    DeviceCommandRespVO getCommand(Long id);

}

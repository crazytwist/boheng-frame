package cn.boheng.frame.module.device.service.http;

import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_PATH_MISSING;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_NOT_EXISTS;

/**
 * 按台账和动作发起一次 HTTP 调用。
 */
@Service
@Validated
public class DeviceHttpService {

    @Resource
    private DeviceInfoService deviceInfoService;
    @Resource
    private DeviceActionMapper deviceActionMapper;
    @Resource
    private DeviceHttpGateway deviceHttpGateway;
    @Resource
    private DeviceCommandService deviceCommandService;

    public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO) {
        DeviceInfoDO device = deviceInfoService.getDevice(reqVO.getDeviceId());
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        String connection = device.getConnectionType();
        if (!"HTTP".equals(connection) && !"NODE_RED".equals(connection)) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "接入方式不是 HTTP");
        }
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(device.getDeviceTypeCode(), reqVO.getActionCode());
        if (action == null) {
            throw exception(DEVICE_ACTION_NOT_EXISTS);
        }
        if (StrUtil.isBlank(action.getRequestPath())) {
            throw exception(DEVICE_ACTION_PATH_MISSING, action.getActionCode());
        }
        String codecCode = StrUtil.blankToDefault(reqVO.getCodecCode(), action.getCodecCode());
        DeviceCommandDO command = deviceCommandService.open(device, action, reqVO.getParamsJson(), codecCode);
        try {
            DeviceInvokeRespVO resp = deviceHttpGateway.invoke(device, action, reqVO.getParamsJson());
            deviceCommandService.complete(command.getId(), resp, codecCode);
            resp.setCommandId(command.getId());
            resp.setCommandNo(command.getCommandNo());
            return resp;
        } catch (RuntimeException ex) {
            deviceCommandService.fail(command.getId(), ex.getMessage());
            throw ex;
        }
    }

}

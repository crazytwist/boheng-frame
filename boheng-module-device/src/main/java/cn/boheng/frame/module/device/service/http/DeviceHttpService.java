package cn.boheng.frame.module.device.service.http;

import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackTicket;
import cn.boheng.frame.module.device.service.action.DeviceParamResolver;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
import cn.boheng.frame.module.device.service.simulate.SimulateHttpService;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandStatusEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.ConnectionTypeEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.DeviceStatusEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.ResultModeEnum;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_DISABLED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_PATH_MISSING;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CALLBACK_DISABLED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_UNAVAILABLE;

/**
 * 按台账和动作发起一次 HTTP 调用。
 */
@Service
@Validated
public class DeviceHttpService {
    /**
     * 设备台账服务
     */

    @Resource
    private DeviceInfoService deviceInfoService;
    /**
     * 设备动作表
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;
    /**
     * 设备 HTTP 访问
     */
    @Resource
    private DeviceHttpGateway deviceHttpGateway;
    /**
     * 命令记录服务
     */
    @Resource
    private DeviceCommandService deviceCommandService;
    /**
     * 仿真 HTTP。台账标了仿真，或动作配了仿真报文时走这里
     */
    @Resource
    private SimulateHttpService simulateHttpService;
    /**
     * 设备回调。动作要等回调时签发回调地址并等待
     */
    @Resource
    private DeviceCallbackService deviceCallbackService;
    /**
     * 设备动作输入契约解析器，统一展开默认值并校验最终参数
     */
    @Resource
    private DeviceParamResolver deviceParamResolver;

    /**
     * 校验设备和动作后发起调用，并记下命令。来源按人工测试记录
     */
    public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO) {
        return invoke(reqVO, null);
    }

    /**
     * 校验设备和动作后发起调用，并记下命令。
     *
     * @param source 命令来源。流程引擎下发时传 FLOW 来源（来源类型 / 来源引用 / 尝试次数），
     *               事后能沿 source_ref 找回是哪个运行哪个节点哪次尝试发的；为空按人工测试记
     */
    public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO, DeviceCommandService.CommandSource source) {
        DeviceInfoDO device = deviceInfoService.getDevice(reqVO.getDeviceId());
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(device.getDeviceTypeCode(), reqVO.getActionCode());
        if (action == null) {
            throw exception(DEVICE_ACTION_NOT_EXISTS);
        }
        if (action.getStatus() != null && action.getStatus() != 0) {
            throw exception(DEVICE_ACTION_DISABLED, action.getActionCode());
        }
        boolean simulate = simulateHttpService.supports(device, action.getActionCode());
        if (!simulate && (DeviceStatusEnum.MAINTENANCE.getStatus().equals(device.getStatus())
                || DeviceStatusEnum.FAULT.getStatus().equals(device.getStatus()))) {
            throw exception(DEVICE_UNAVAILABLE, device.getDeviceCode(),
                    DeviceStatusEnum.MAINTENANCE.getStatus().equals(device.getStatus())
                            ? DeviceStatusEnum.MAINTENANCE.getName()
                            : DeviceStatusEnum.FAULT.getName());
        }
        if (!simulate) {
            String connection = device.getConnectionType();
            if (!ConnectionTypeEnum.HTTP.getType().equals(connection)
                    && !ConnectionTypeEnum.NODE_RED.getType().equals(connection)) {
                throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "接入方式不是 HTTP");
            }
            if (StrUtil.isBlank(action.getRequestPath())) {
                throw exception(DEVICE_ACTION_PATH_MISSING, action.getActionCode());
            }
        }
        boolean callback = simulate
                ? simulateHttpService.expectsCallback(device, action.getActionCode())
                : ResultModeEnum.CALLBACK.getMode().equals(action.getResultMode());
        if (callback && !simulate && !Boolean.TRUE.equals(device.getCallbackEnabled())) {
            throw exception(DEVICE_CALLBACK_DISABLED, device.getDeviceCode());
        }
        String resolvedParamsJson = deviceParamResolver.resolve(action.getParamSchema(), reqVO.getParamsJson());
        String codecCode = StrUtil.blankToDefault(reqVO.getCodecCode(), action.getCodecCode());
        DeviceCommandDO command = deviceCommandService.open(device, action, resolvedParamsJson, codecCode, source);
        DeviceCallbackTicket ticket = null;
        try {
            if (callback) {
                ticket = deviceCallbackService.issue(device, action, command.getCommandNo(), simulate);
                deviceCommandService.markWaitingCallback(command.getId(), ticket.tokenHash(), ticket.timeoutAt());
            }
            DeviceInvokeRespVO resp = simulate
                    ? simulateHttpService.exchange(device, action, resolvedParamsJson, command.getId(),
                            command.getCommandNo(), ticket)
                    : deviceHttpGateway.invoke(device, action, resolvedParamsJson, command.getCommandNo(), ticket);
            if (callback) {
                acceptCallback(device, command, resp);
                resp.setStatus(202);
                resp.setCommandStatus(CommandStatusEnum.WAITING.getStatus());
            } else {
                deviceCommandService.complete(command.getId(), resp, codecCode);
                resp.setCommandStatus(resp.getStatus() != null && resp.getStatus() >= 200 && resp.getStatus() < 300
                        ? CommandStatusEnum.SUCCEEDED.getStatus() : CommandStatusEnum.FAILED.getStatus());
            }
            resp.setCommandId(command.getId());
            resp.setCommandNo(command.getCommandNo());
            return resp;
        } catch (RuntimeException ex) {
            deviceCommandService.fail(command.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * 记录设备受理应答。受理成功后调用方立即返回，命令继续保持等待回调状态。
     */
    private void acceptCallback(DeviceInfoDO device, DeviceCommandDO command, DeviceInvokeRespVO ack) {
        deviceCommandService.recordSent(command.getId(), ack);
        boolean accepted = ack.getStatus() != null && ack.getStatus() >= 200 && ack.getStatus() < 300;
        if (!accepted) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(),
                    "设备没有受理：" + StrUtil.blankToDefault(ack.getBody(), "HTTP " + ack.getStatus()));
        }
    }

}

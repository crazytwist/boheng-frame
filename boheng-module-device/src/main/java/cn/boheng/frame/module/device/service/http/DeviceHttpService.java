package cn.boheng.frame.module.device.service.http;

import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackTicket;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
import cn.boheng.frame.module.device.service.simulate.SimulateHttpService;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_DISABLED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_PATH_MISSING;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_BUSY;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CALLBACK_DISABLED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_LOCKED;
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
     * 设备台账表，用来抢占和释放设备
     */
    @Resource
    private DeviceInfoMapper deviceInfoMapper;
    /**
     * 命令表，用来数设备上的在途命令
     */
    @Resource
    private DeviceCommandMapper deviceCommandMapper;

    /**
     * 不等回调的调用占用设备多久。同步最长 20 秒、轮询最长 10 分钟，再留些余量
     */
    private static final long OCCUPY_SEC = 15 * 60;

    /**
     * 校验设备和动作后发起调用，并记下命令
     */
    public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO) {
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
        if (!simulate && ("MAINTENANCE".equals(device.getStatus()) || "FAULT".equals(device.getStatus()))) {
            throw exception(DEVICE_UNAVAILABLE, device.getDeviceCode(),
                    "MAINTENANCE".equals(device.getStatus()) ? "维护中" : "故障");
        }
        ensureNotLocked(device);
        if (!simulate) {
            String connection = device.getConnectionType();
            if (!"HTTP".equals(connection) && !"NODE_RED".equals(connection)) {
                throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "接入方式不是 HTTP");
            }
            if (StrUtil.isBlank(action.getRequestPath())) {
                throw exception(DEVICE_ACTION_PATH_MISSING, action.getActionCode());
            }
        }
        boolean callback = simulate
                ? simulateHttpService.expectsCallback(device, action.getActionCode())
                : "CALLBACK".equals(action.getResultMode());
        if (callback && !simulate && !Boolean.TRUE.equals(device.getCallbackEnabled())) {
            throw exception(DEVICE_CALLBACK_DISABLED, device.getDeviceCode());
        }
        String codecCode = StrUtil.blankToDefault(reqVO.getCodecCode(), action.getCodecCode());
        DeviceCommandDO command = deviceCommandService.open(device, action, reqVO.getParamsJson(), codecCode);
        boolean occupied = occupy(device, action, command, callback);
        DeviceCallbackTicket ticket = null;
        try {
            if (callback) {
                ticket = deviceCallbackService.issue(device, action, command.getCommandNo(), simulate);
                deviceCommandService.awaitCallback(command.getId(), ticket.tokenHash(), ticket.timeoutAt());
            }
            DeviceInvokeRespVO resp = simulate
                    ? simulateHttpService.exchange(device, action, reqVO.getParamsJson(), command.getId(),
                            command.getCommandNo(), ticket)
                    : deviceHttpGateway.invoke(device, action, reqVO.getParamsJson(), command.getCommandNo(), ticket);
            if (callback) {
                resp = waitCallback(device, command, ticket, resp);
            } else {
                deviceCommandService.complete(command.getId(), resp, codecCode);
            }
            resp.setCommandId(command.getId());
            resp.setCommandNo(command.getCommandNo());
            return resp;
        } catch (RuntimeException ex) {
            deviceCommandService.fail(command.getId(), ex.getMessage());
            throw ex;
        } finally {
            if (occupied) {
                deviceInfoMapper.releaseOccupy(device.getId(), command.getId());
            }
        }
    }

    /**
     * 人工锁或维护锁还没过期时拒绝调用。命令占用另由抢占处理。
     */
    private void ensureNotLocked(DeviceInfoDO device) {
        String lockType = device.getLockType();
        if (StrUtil.isBlank(lockType) || "COMMAND".equals(lockType)) {
            return;
        }
        LocalDateTime expire = device.getLockExpireTime();
        if (expire != null && expire.isBefore(LocalDateTime.now())) {
            return;
        }
        throw exception(DEVICE_LOCKED, device.getDeviceCode(), lockType, StrUtil.blankToDefault(device.getLockReason(), "未填写"));
    }

    /**
     * 按并发策略占用设备。独占和平台排队一次只放一条；仪器自带队列的按在途上限放行。
     * 抢不到时命令记为失败并提示设备忙。
     *
     * @return 是否加了占用锁，结束时要释放
     */
    private boolean occupy(DeviceInfoDO device, DeviceActionDO action, DeviceCommandDO command, boolean callback) {
        if ("DEVICE_QUEUED".equals(device.getConcurrencyPolicy())) {
            int limit = device.getMaxInflight() == null || device.getMaxInflight() < 1 ? 1 : device.getMaxInflight();
            if (deviceCommandMapper.countRunning(device.getId()) > limit) {
                busy(device, command);
            }
            return false;
        }
        long holdSec = callback ? deviceCallbackService.timeoutSec(device, action) + 60L : OCCUPY_SEC;
        LocalDateTime expireAt = LocalDateTime.now().plusSeconds(holdSec);
        if (!deviceInfoMapper.tryOccupy(device.getId(), command.getId(), command.getCommandNo(), expireAt)) {
            busy(device, command);
        }
        return true;
    }

    /**
     * 设备正忙：命令留痕为失败，再抛出
     */
    private void busy(DeviceInfoDO device, DeviceCommandDO command) {
        deviceCommandService.fail(command.getId(), "设备正忙，未下发");
        throw exception(DEVICE_BUSY, device.getDeviceCode());
    }

    /**
     * 设备受理后停下来等回调。受理就被拒时直接按失败处理；等到后把回调正文当作这次调用的结果返回。
     */
    private DeviceInvokeRespVO waitCallback(DeviceInfoDO device, DeviceCommandDO command, DeviceCallbackTicket ticket,
                                            DeviceInvokeRespVO ack) {
        deviceCommandService.recordSent(command.getId(), ack);
        boolean accepted = ack.getStatus() != null && ack.getStatus() >= 200 && ack.getStatus() < 300;
        if (!accepted) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(),
                    "设备没有受理：" + StrUtil.blankToDefault(ack.getBody(), "HTTP " + ack.getStatus()));
        }
        deviceCallbackService.await(command.getId(), device.getDeviceCode(), ticket);
        DeviceCommandRespVO done = deviceCommandService.getCommand(command.getId());
        DeviceInvokeRespVO resp = new DeviceInvokeRespVO();
        resp.setStatus("SUCCEEDED".equals(done.getStatus()) ? 200 : 502);
        resp.setBody(StrUtil.blankToDefault(done.getRawBody(), done.getErrorMsg()));
        resp.setContentType(ack.getContentType());
        resp.setRetriedLogin(ack.getRetriedLogin());
        resp.setHttpMethod(ack.getHttpMethod());
        resp.setRequestUrl(ack.getRequestUrl());
        resp.setRequestBody(ack.getRequestBody());
        return resp;
    }

}

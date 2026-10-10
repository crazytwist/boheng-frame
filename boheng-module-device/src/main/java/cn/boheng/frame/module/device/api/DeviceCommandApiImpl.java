package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceCommandSendReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.service.http.DeviceHttpService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 设备命令契约实现。给流程引擎用的下发入口：
 * 复用 {@link DeviceHttpService} 的「校验 → 占用 → 落命令 → 下发 → 等回调 → 超时」全流程，
 * 把所有异常收成失败 DTO，不跨模块抛业务异常。
 *
 * <p>溯源说明：「哪个运行哪个节点哪次尝试发的哪条命令」由 Flow 侧
 * flow_step_execution.command_id 承担；设备命令表按调用入口记 MANUAL。
 * 设备域 v4 的 business_exec_id / node_id 幂等三键就绪后再把 FLOW 来源接上。
 */
@Service
@Validated
public class DeviceCommandApiImpl implements DeviceCommandApi {

    /**
     * 设备台账表。按编码找设备主键
     */
    @Resource
    private DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备调用全流程
     */
    @Resource
    private DeviceHttpService deviceHttpService;

    /**
     * 下发并等终态。设备忙、动作停用、网络失败、超时都是 success=false，不抛异常
     */
    @Override
    public DeviceCommandResultDTO send(DeviceCommandSendReqDTO req) {
        DeviceCommandResultDTO result = new DeviceCommandResultDTO();
        DeviceInfoDO device = StrUtil.isBlank(req.getDeviceCode())
                ? null : deviceInfoMapper.selectByDeviceCode(req.getDeviceCode().trim());
        if (device == null) {
            result.setSuccess(false);
            result.setErrorMessage("设备不存在: " + req.getDeviceCode());
            return result;
        }
        DeviceInvokeReqVO invokeReq = new DeviceInvokeReqVO();
        invokeReq.setDeviceId(device.getId());
        invokeReq.setActionCode(req.getActionCode());
        invokeReq.setParamsJson(req.getParamsJson());
        try {
            DeviceInvokeRespVO resp = deviceHttpService.invoke(invokeReq);
            boolean ok = resp.getStatus() != null && resp.getStatus() >= 200 && resp.getStatus() < 300;
            result.setCommandId(resp.getCommandId());
            result.setCommandNo(resp.getCommandNo());
            result.setSuccess(ok);
            result.setOutputJson(resp.getBody());
            if (!ok) {
                result.setErrorMessage("设备返回 HTTP " + resp.getStatus());
            }
            return result;
        } catch (RuntimeException ex) {
            // 设备域的业务异常（忙/停用/超时等）在这里收口，Flow 只看结果
            result.setSuccess(false);
            result.setErrorMessage(StrUtil.maxLength(StrUtil.nullToEmpty(ex.getMessage()), 512));
            return result;
        }
    }

}

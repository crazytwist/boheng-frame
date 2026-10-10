package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceCommandSendReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;

/**
 * 设备命令契约（Flow 引擎专用）。
 *
 * <p>设备域内部已有完整的「校验 → 占用 → 落命令 → 下发 → 等回调 → 超时」链路，
 * 本契约把这条链路暴露给流程引擎：按 deviceCode + actionCode 下发，
 * 阻塞等到终态后返回结果。所有异常收成失败 DTO，不跨模块抛业务异常。
 *
 * <p>调用方注意事项：本方法会阻塞（等设备回调），必须放在独立线程池里调，
 * 不能挂在 HTTP 请求线程上；跨租户调用需自带租户上下文。
 */
public interface DeviceCommandApi {

    /**
     * 下发一条命令并等到终态
     *
     * @param req 请求（deviceCode + actionCode + paramsJson + 溯源信息）
     * @return 终态结果。设备忙、动作停用、超时都体现为 success=false
     */
    DeviceCommandResultDTO send(DeviceCommandSendReqDTO req);

}

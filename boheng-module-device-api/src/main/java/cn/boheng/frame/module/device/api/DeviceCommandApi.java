package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceCommandSendReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;

/**
 * 设备命令契约（Flow 引擎专用）。
 *
 * <p>按 deviceCode + actionCode 下发。同步动作直接返回终态；回调动作在设备受理后
 * 返回 WAITING，调用方持久化 commandId，之后通过 {@link #getResult(Long)} 查询终态。
 */
public interface DeviceCommandApi {

    /**
     * 下发一条命令。回调模式只等设备受理，不等待执行完成
     *
     * @param req 请求（deviceCode + actionCode + paramsJson + 溯源信息）
     * @return 同步动作返回终态；回调动作返回 WAITING；下发失败时 success=false
     */
    DeviceCommandResultDTO send(DeviceCommandSendReqDTO req);

    /**
     * 查询一条已下发命令的当前结果。
     *
     * @param commandId 命令编号
     * @return 当前结果，命令不存在时返回失败结果
     */
    DeviceCommandResultDTO getResult(Long commandId);

}

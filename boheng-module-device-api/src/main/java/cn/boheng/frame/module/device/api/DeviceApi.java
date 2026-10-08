package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceSelectReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;

import java.util.List;

/**
 * 设备域对外 API（契约层，提供给其它模块调用）
 *
 * 一期只定义「选设备」契约——这是调度域唯一需要跨模块拿的东西。
 * 「发命令 / 查命令状态 / 收结果」的契约（DeviceCommandApi）随命令执行模型一起落地时再补。
 *
 * 实现位于 boheng-module-device 的 DeviceApiImpl，由 Spring 注入；
 * 消费方（调度域等）只依赖本接口 + DTO，绝不依赖实现模块。
 *
 * @author yinan
 */
public interface DeviceApi {

    /**
     * 按条件选择可用设备
     *
     * 调度域给出「类型 + 能力 + 在线 + 空闲」条件，设备域返回满足条件的设备列表。
     * 不硬编 device_code —— 选设备是设备域的职责（见架构文档 §二）。
     *
     * @param reqVO 选择条件
     * @return 满足条件的设备摘要列表（按条件排序，空列表=无可用设备）
     */
    List<DeviceSummaryDTO> selectDevices(DeviceSelectReqDTO reqVO);

}

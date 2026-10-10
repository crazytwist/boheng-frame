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

    /**
     * 按编码取一台设备。流程发布时用来确认步骤指向的设备还在台账里。
     *
     * @param deviceCode 设备编码
     * @return 设备摘要；编码为空或台账没有这台设备时返回 null
     */
    DeviceSummaryDTO getDeviceByCode(String deviceCode);

    /**
     * 该设备类型下是否有一条启用的动作。流程发布时用来确认这一步还能下发。
     *
     * @param deviceTypeCode 设备类型编码，取台账上的类型，不取前端另传的类型
     * @param actionCode 动作编码
     * @return 动作存在且未停用时为 true
     */
    boolean hasEnabledAction(String deviceTypeCode, String actionCode);

}

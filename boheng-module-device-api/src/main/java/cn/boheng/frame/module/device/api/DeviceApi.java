package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceSelectReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.device.api.dto.DeviceParamSetDTO;

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

    /**
     * 取一台设备的能力集：该设备类型下所有启用动作的编码。
     *
     * <p>设备任务认领时，机器端声明自己操作哪台设备（deviceCode），平台按这个能力集
     * 匹配「不限设备 + 我会做的类型」的任务。与 selectDevices（选设备）正交——
     * 这个是「已知设备，反查它都会做什么」，供任务队列按能力路由用。
     *
     * @param deviceCode 设备编码
     * @return 启用动作编码列表；设备不存在或没有启用动作时返回空列表
     */
    List<String> listActionCodesByDevice(String deviceCode);

    /**
     * 读取一套可用于指定设备动作的参数集。
     *
     * <p>参数集必须存在、启用、已验证，且其设备类型及动作须与目标设备一致；
     * 任一条件不满足时返回 {@code null}，让调用方以自身上下文生成错误提示。
     *
     * @param paramSetId 参数集主键
     * @param deviceCode 目标设备编码
     * @param actionCode 目标动作编码
     * @return 可执行的参数集；不可用时返回 null
     */
    DeviceParamSetDTO getUsableParamSet(Long paramSetId, String deviceCode, String actionCode);

}

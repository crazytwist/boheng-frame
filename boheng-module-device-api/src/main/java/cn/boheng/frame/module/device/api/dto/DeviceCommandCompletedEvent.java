package cn.boheng.frame.module.device.api.dto;

/** 设备命令进入终态后的跨模块完成事件。 */
public record DeviceCommandCompletedEvent(Long commandId, String commandNo, String status) {
}

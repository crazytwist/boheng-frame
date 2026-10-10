package cn.boheng.frame.module.device.api.dto;

import lombok.Data;

/**
 * Flow 引擎下发给设备的命令请求。
 */
@Data
public class DeviceCommandSendReqDTO {

    /**
     * 台账上的设备编码（不是数据库主键）
     */
    private String deviceCode;

    /**
     * 该设备类型下的动作编码
     */
    private String actionCode;

    /**
     * 参数 JSON，对应动作请求模板的占位符
     */
    private String paramsJson;

    /**
     * 等待终态的毫秒数。超时按失败收场
     */
    private Long timeoutMs;

    /**
     * 命令来源。设备域 MANUAL / 流程域 FLOW
     */
    private String sourceType;

    /**
     * 来源引用，如 flow:{executionId}:{nodeId}:{attempt}
     */
    private String sourceRef;

    /**
     * 同一业务节点的第几次尝试，从 1 开始
     */
    private Integer attempt;

}

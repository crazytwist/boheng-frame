package cn.boheng.frame.module.device.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 设备选择条件 DTO（契约层）
 *
 * 调度域「选设备」时只给条件，不硬编 device_code（设备域职责，见架构文档 §二）。
 * 设备域按条件返回满足「类型 + 能力 + 在线 + 空闲」的设备。
 *
 * @author yinan
 */
@Data
public class DeviceSelectReqDTO implements Serializable {

    /**
     * 设备类型编码（必填，如 PLATE_READER）
     */
    private String deviceTypeCode;

    /**
     * 需要的动作编码（可空；填了则只返回声明了该动作的设备）
     */
    private List<String> actionCodes;

    /**
     * 是否要求空闲（true=只返回无占用锁的设备；默认 true）
     */
    private Boolean requireIdle;

    /**
     * 是否要求在线（true=只返回 ONLINE 的设备；默认 true）
     */
    private Boolean requireOnline;

    /**
     * 返回数量上限（默认 1，即「选一台」）
     */
    private Integer limit;

}

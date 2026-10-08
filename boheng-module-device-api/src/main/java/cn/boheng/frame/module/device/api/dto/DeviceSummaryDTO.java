package cn.boheng.frame.module.device.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备摘要 DTO（契约层）
 *
 * 「选设备」的结果：设备域返回给调度域的最小设备信息。
 * 只带调度域做「选哪台、发命令」需要的字段，不带任何 DAL/实现细节。
 *
 * @author yinan
 */
@Data
public class DeviceSummaryDTO implements Serializable {

    /**
     * 设备编号
     */
    private Long id;

    /**
     * 设备编码（跨模块寻址只认编码，不建外键）
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型编码（行为建模，如 PLATE_READER）
     */
    private String deviceTypeCode;

    /**
     * 在线状态（ONLINE/OFFLINE/MAINTENANCE/FAULT）
     */
    private String status;

    /**
     * 是否空闲（无占用锁 / 无进行中命令）
     */
    private Boolean idle;

}

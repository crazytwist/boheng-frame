package cn.boheng.frame.module.device.api.dto;

import lombok.Data;

/**
 * Flow 引擎拿到的一次设备命令结果。下发成功但仍等回调时状态为 WAITING，
 * Flow 持久化等待并在之后查询终态，不占用执行线程。
 */
@Data
public class DeviceCommandResultDTO {

    /**
     * 命令编号
     */
    private Long commandId;

    /**
     * 对外命令号
     */
    private String commandNo;

    /**
     * 是否成功到达终态
     */
    private Boolean success;

    /**
     * 命令状态：WAITING / SUCCEEDED / FAILED / TIMED_OUT / CANCELLED
     */
    private String status;

    /**
     * 响应正文。成功时是设备返回的内容
     */
    private String outputJson;

    /**
     * 失败时的错误说明
     */
    private String errorMessage;

}

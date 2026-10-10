package cn.boheng.frame.module.device.api.dto;

import lombok.Data;

/**
 * Flow 引擎拿到的一次设备命令结果。
 * 设备域把所有异常收成失败 DTO，不跨模块抛业务异常——
 * Flow 拿到 success=false 就按节点失败处理，重试决策在 Flow 侧。
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
     * 响应正文。成功时是设备返回的内容
     */
    private String outputJson;

    /**
     * 失败时的错误说明
     */
    private String errorMessage;

}

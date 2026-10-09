package cn.boheng.frame.module.device.controller.admin.command.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * 命令记录
 */

@Data
public class DeviceCommandRespVO {

    /**
     * 主键，雪花算法
     */
    private Long id;
    /**
     * 对外命令号
     */
    private String commandNo;
    /**
     * 调用来源。当前测试入口记为 MANUAL
     */
    private String sourceType;
    /**
     * 设备编号
     */
    private Long deviceId;
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 设备类型编码
     */
    private String deviceTypeCode;
    /**
     * 动作编码
     */
    private String actionCode;
    /**
     * 下发方式。当前同步测试记为 SYNC
     */
    private String dispatchMode;
    /**
     * 结果回收方式。当前同步返回记为 SYNC_RETURN
     */
    private String resultMode;
    /**
     * 命令状态：ACQUIRED 已占用，SUCCEEDED 成功，FAILED 失败
     */
    private String status;
    /**
     * 下发时的参数快照
     */
    private String paramsJson;
    /**
     * 实际发出的报文
     */
    private String payloadJson;
    /**
     * 请求方法、地址和正文
     */
    private String requestJson;
    /**
     * 响应状态、类型和正文
     */
    private String responseJson;
    /**
     * 失败或解析失败时的说明
     */
    private String errorMsg;
    /**
     * 解析规则编码
     */
    private String codecCode;
    /**
     * 关联的原始响应编号
     */
    private Long dataRawId;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 占用设备的时间
     */
    private LocalDateTime acquiredAt;
    /**
     * 请求发出的时间
     */
    private LocalDateTime sentAt;
    /**
     * 进入终态的时间
     */
    private LocalDateTime finishedAt;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    @Schema(description = "原始响应正文")
    private String rawBody;

    @Schema(description = "原始响应格式")
    private String rawFormat;

}

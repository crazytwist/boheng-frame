package cn.boheng.frame.module.device.dal.dataobject.command;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 一次设备调用的事实。业务侧只追加，不提供删除。
 */
@TableName("device_command")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandDO extends TenantBaseDO {
    /**
     * 主键，雪花算法
     */

    @TableId(type = IdType.ASSIGN_ID)
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
     * 同一业务节点的第几次尝试，从 1 开始
     */
    private Integer attempt;

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
     * 已轮询次数。同步调用恒为 0
     */
    private Integer pollCount;

    /**
     * 等待原因。同步调用为空
     */
    private String waitingFor;

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
     * 操作者类型。人工测试记为 USER
     */
    private String operatorType;

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
     * 乐观锁版本
     */
    private Integer version;

}

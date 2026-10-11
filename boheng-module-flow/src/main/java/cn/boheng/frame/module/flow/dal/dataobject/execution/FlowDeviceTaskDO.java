package cn.boheng.frame.module.flow.dal.dataobject.execution;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 流程设备异步任务队列。节点执行时投递一行，机器端拉取（认领）执行后回报完成。
 * 任务不是「推」给设备的，而是「存」在队列里由机器端主动拉取——设备离线也能排队等。
 */
@TableName("flow_device_task")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowDeviceTaskDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 对外任务号。机器端回报时按它定位
     */
    private String taskNo;

    /**
     * 完成回报校验令牌。入队生成，认领时下发，回报时校验
     */
    private String callbackToken;

    /**
     * 所属运行
     */
    private Long executionId;

    /**
     * 节点编码
     */
    private String nodeId;

    /**
     * 尝试序号
     */
    private Integer attempt;

    /**
     * 循环轮次
     */
    private Integer iterationNo;

    /**
     * 目标设备编码。空 = 不限设备
     */
    private String deviceCode;

    /**
     * 任务类型 / 动作编码
     */
    private String actionCode;

    /**
     * 任务参数 JSON
     */
    private String paramsJson;

    /**
     * PENDING 待领取 / CLAIMED 已领取执行中 / DONE 完成 / FAILED 失败 / CANCELLED 已取消
     */
    private String status;

    /**
     * 认领的机器端标识
     */
    private String claimedBy;

    /**
     * 认领时间
     */
    private LocalDateTime claimedAt;

    /**
     * 排队耗时 = 认领时间 - 创建时间，毫秒
     */
    private Long waitMs;

    /**
     * 完成结果 JSON（写进节点输出）
     */
    private String resultJson;

    /**
     * 失败说明
     */
    private String errorMessage;

    /**
     * 执行耗时 = 终态时间 - 认领时间，毫秒
     */
    private Long executeMs;

}

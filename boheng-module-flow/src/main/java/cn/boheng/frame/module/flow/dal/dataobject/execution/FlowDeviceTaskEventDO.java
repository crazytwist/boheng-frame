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
 * 设备任务事件流水。一次状态流转一行，INSERT-ONLY 只增不删。
 */
@TableName("flow_device_task_event")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowDeviceTaskEventDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联任务号
     */
    private String taskNo;

    /**
     * 事件类型：CREATED / CLAIMED / DONE / FAILED / CANCELLED
     */
    private String eventType;

    /**
     * 变更前状态
     */
    private String fromStatus;

    /**
     * 变更后状态
     */
    private String toStatus;

    /**
     * 当时操作的机器端标识
     */
    private String claimedBy;

    /**
     * 附加信息 JSON
     */
    private String payload;

    /**
     * 事件发生时间
     */
    private LocalDateTime occurredAt;

}

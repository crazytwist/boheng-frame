package cn.boheng.frame.module.transport.dal.dataobject;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Transport 运输需求 DO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("transport_request")
public class TransportRequestDO extends TenantBaseDO {
    /**
     * 主键。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 需求编号。
     */
    private String requestNo;
    /**
     * 需求类型。
     */
    private String requestType;
    /**
     * 来源类型。
     */
    private String sourceType;
    /**
     * 来源引用。
     */
    private String sourceRef;
    /**
     * 项目编号。
     */
    private Long projectId;
    /**
     * 实验编号。
     */
    private Long experimentId;
    /**
     * 流程执行编号。
     */
    private Long executionId;
    /**
     * 优先级。
     */
    private Integer priority;
    /**
     * 最早开始时间。
     */
    private LocalDateTime earliestStartTime;
    /**
     * 最晚完成时间。
     */
    private LocalDateTime latestFinishTime;
    /**
     * 运输模式。
     */
    private String transportMode;
    /**
     * 约束快照。
     */
    private String constraintJson;
    /**
     * 状态。
     */
    private String status;
    /**
     * 当前运输单。
     */
    private Long activeOrderId;
    /**
     * 幂等键。
     */
    private String idempotencyKey;
    /**
     * 备注。
     */
    private String remark;
}

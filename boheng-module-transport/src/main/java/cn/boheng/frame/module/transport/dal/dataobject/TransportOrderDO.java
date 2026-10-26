package cn.boheng.frame.module.transport.dal.dataobject;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

/** Transport 可执行运输单 DO。 */
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
@TableName("transport_order")
public class TransportOrderDO extends TenantBaseDO {
    /** 主键。 */ @TableId(type = IdType.ASSIGN_ID) private Long id;
    /** 运输单号。 */ private String orderNo;
    /** 来源需求。 */ private Long requestId;
    /** 方案版本。 */ private Integer planVersion;
    /** 执行器类型。 */ private String primaryExecutorType;
    /** 执行器 ID。 */ private Long primaryExecutorId;
    /** 执行器编码。 */ private String primaryExecutorCode;
    /** 分配模式。 */ private String allocationMode;
    /** 分配引用。 */ private String allocationRef;
    /** 当前停靠序号。 */ private Integer currentStopSeq;
    /** 路线快照。 */ private String routePlanJson;
    /** 评分快照。 */ private String scoreDetailJson;
    /** 状态。 */ private String status;
    /** 下发时间。 */ private LocalDateTime dispatchAt;
    /** 开始时间。 */ private LocalDateTime startAt;
    /** 完成时间。 */ private LocalDateTime finishAt;
    /** 失败编码。 */ private String failureCode;
    /** 失败说明。 */ private String failureMessage;
    /** 乐观锁版本。 */ @Version private Integer version;
}

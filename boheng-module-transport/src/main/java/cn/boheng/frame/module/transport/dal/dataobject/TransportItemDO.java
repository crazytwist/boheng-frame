package cn.boheng.frame.module.transport.dal.dataobject;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Transport 运输对象明细 DO。 */
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
@TableName("transport_item")
public class TransportItemDO extends TenantBaseDO {
    /** 主键。 */ @TableId(type = IdType.ASSIGN_ID) private Long id;
    /** 明细编号。 */ private String itemNo;
    /** 运输单。 */ private Long orderId;
    /** 需求。 */ private Long requestId;
    /** 物料实例。 */ private Long materialInstanceId;
    /** 载具。 */ private Long loadUnitId;
    /** 载具编码。 */ private String loadUnitCode;
    /** 批次。 */ private String batchNo;
    /** 数量。 */ private BigDecimal quantity;
    /** 源槽位。 */ private Long sourceSlotId;
    /** 源槽位编码。 */ private String sourceSlotCode;
    /** 目标槽位。 */ private Long targetSlotId;
    /** 目标槽位编码。 */ private String targetSlotCode;
    /** 当前槽位。 */ private Long currentSlotId;
    /** 当前槽位编码。 */ private String currentSlotCode;
    /** 运输载位。 */ private Long transportLoadSlotId;
    /** 取料停靠点。 */ private Long pickupStopId;
    /** 放料停靠点。 */ private Long dropoffStopId;
    /** 混载组。 */ private String mixGroup;
    /** 顺序。 */ private Integer sequenceNo;
    /** 状态。 */ private String status;
    /** WMS 移动流水。 */ private Long lastMovementId;
    /** 装载时间。 */ private LocalDateTime loadedAt;
    /** 卸载时间。 */ private LocalDateTime unloadedAt;
    /** 乐观锁版本。 */ @Version private Integer version;
}

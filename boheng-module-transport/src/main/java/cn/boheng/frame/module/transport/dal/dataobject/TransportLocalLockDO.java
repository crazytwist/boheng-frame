package cn.boheng.frame.module.transport.dal.dataobject;
import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO; import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.LocalDateTime;
/** Transport V1 本地资源锁 DO。 */ @Data @EqualsAndHashCode(callSuper=true) @TableName("transport_local_lock") @Builder @NoArgsConstructor @AllArgsConstructor
public class TransportLocalLockDO extends TenantBaseDO { @TableId(type=IdType.ASSIGN_ID) private Long id; private String resourceType; private String resourceKey; private Long orderId; private String lockToken; private Long fencingToken; private String status; private LocalDateTime acquiredAt; private LocalDateTime expireAt; private LocalDateTime heartbeatAt; private LocalDateTime releasedAt; @Version private Integer version; }

package cn.boheng.frame.module.flow.dal.dataobject.error;

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

@TableName("flow_error_record")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowErrorRecordDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String errorNo;
    private String dedupKey;
    private String sourceType;
    private String sourceRefId;
    private Long executionId;
    private String nodeId;
    private Integer iterationNo;
    private Integer attempt;
    private String deviceCode;
    private Long commandId;
    private Long deviceEventId;
    private String operationId;
    private String errorCode;
    private String category;
    private String severity;
    private String certainty;
    private String retryMode;
    private String message;
    private String detailJson;
    private String rawPayloadRef;
    private String status;
    private String resolution;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime occurredAt;
}

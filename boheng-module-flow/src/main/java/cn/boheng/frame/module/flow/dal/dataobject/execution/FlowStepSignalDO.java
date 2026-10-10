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
 * 统一信号收件箱。设备回调、人工确认、定时到点、子流程终态
 * 都先落一行再由信号路由器消费——幂等去重靠 signal_id 唯一键。
 */
@TableName("flow_step_signal")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStepSignalDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 来源方幂等 ID。同一信号重发不重复消费
     */
    private String signalId;

    /**
     * 目标运行
     */
    private Long executionId;

    /**
     * 目标节点
     */
    private String nodeId;

    /**
     * 目标尝试序号
     */
    private Integer attempt;

    /**
     * 循环轮次
     */
    private Integer iterationNo;

    /**
     * MANUAL / TIMER / CHILD / EXTERNAL
     */
    private String signalType;

    /**
     * 信号内容 JSON。人工备注、子运行结果等
     */
    private String payload;

    /**
     * 信号结论：true 放行 / false 拒绝（人工否决）
     */
    private Boolean success;

    /**
     * RECEIVED 已收 / APPLIED 已消费 / DUPLICATE 重复 / IGNORED 无目标 / REJECTED 已拒绝
     */
    private String processStatus;

    /**
     * 收到时间
     */
    private LocalDateTime receivedAt;

    /**
     * 消费时间
     */
    private LocalDateTime processedAt;

}

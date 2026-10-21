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
 * 一次节点执行尝试。重试新行不覆盖（attempt 递增），
 * 循环场景 iteration_no 区分轮次——「第 2 轮第 1 次」和「第 1 轮第 2 次」是不同的行。
 */
@TableName("flow_step_execution")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStepExecutionDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属运行
     */
    private Long executionId;

    /**
     * 节点编码
     */
    private String nodeId;

    /**
     * 启动时冻结的步骤类型 key
     */
    private String stepType;

    /**
     * 循环轮次，从 0 开始。非循环节点恒为 0
     */
    private Integer iterationNo;

    /**
     * 本轮的尝试序号，从 1 开始。重试新行不覆盖
     */
    private Integer attempt;

    /**
     * PENDING 待执行 / RUNNING 执行中 / WAITING 等外部 / SUCCESS 成功 /
     * FAILED 失败可重试 / DEAD 失败且次数用尽 / SKIPPED 被裁剪 / CANCELLED 已取消
     */
    private String status;

    /**
     * 仅 SKIPPED 时有值：BRANCH 出口未选中 / FAILURE 前置失败 / UPSTREAM 上游连带
     */
    private String skipReason;

    /**
     * 等什么：DEVICE_CALLBACK / DEVICE_COMMAND_CALLBACK / MANUAL / TIMER / CHILD。仅 WAITING 时有值
     */
    private String waitingFor;

    /**
     * 等待的非密引用，如设备命令号、到期时刻
     */
    private String waitRef;

    /**
     * 关联的设备命令编号（device.command 专用）
     */
    private Long commandId;

    /**
     * 关联的设备命令号
     */
    private String commandNo;

    /**
     * 路由节点选中的出口
     */
    private String selectedBranch;

    /**
     * 输入快照 JSON（参数映射解析后的值）
     */
    private String inputSnapshot;

    /**
     * 已结算的输出 JSON
     */
    private String outputData;

    /**
     * 失败码
     */
    private String errorCode;

    /**
     * 失败说明
     */
    private String errorMessage;

    /**
     * 入队时间
     */
    private LocalDateTime queuedAt;

    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 终态时间
     */
    private LocalDateTime finishedAt;

    /**
     * 等待截止时刻。超时扫描按它找
     */
    private LocalDateTime deadlineAt;

    /**
     * 乐观锁版本
     */
    private Integer rowVersion;

}

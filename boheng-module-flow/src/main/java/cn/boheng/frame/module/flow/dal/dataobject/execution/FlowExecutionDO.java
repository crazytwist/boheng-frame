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
 * 一次流程运行。绑死发布版本和当时的指纹（防定义漂移），
 * 子流程扇出产生的子运行用 parent_* 字段挂回父节点。
 */
@TableName("flow_execution")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowExecutionDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 运行业务号，如 exe_20261010_...。对外展示和日志检索用
     */
    private String executionNo;

    /**
     * 绑定的流程编码（发布时冻结）
     */
    private String pipelineKey;

    /**
     * 绑定的流程版本（发布时冻结）
     */
    private Integer version;

    /**
     * 指向 flow_definition.id
     */
    private Long definitionId;

    /**
     * 启动时冻结的定义指纹，检测漂移
     */
    private String checksum;

    /**
     * PENDING 待启动 / RUNNING 进行中 / SUCCESS 成功 / FAILED 失败 / CANCELLED 已取消
     */
    private String status;

    /**
     * 触发方式：MANUAL 手动 / SCHEDULE 定时 / CHILD 子流程
     */
    private String triggerType;

    /**
     * 触发人
     */
    private String triggeredBy;

    /**
     * 业务关联键，如实验单号
     */
    private String businessKey;

    /**
     * 父运行编号（子流程场景）。顶层运行为空
     */
    private Long parentExecutionId;

    /**
     * 父运行里启动本子运行的节点编码
     */
    private String parentNodeId;

    /**
     * 父节点的第几次尝试。CHILD 信号按它回传，禁止用旧 attempt 推进新 attempt
     */
    private Integer parentAttempt;

    /**
     * FANOUT 扇出的第几组。同一父节点的多个子运行按它区分；SINGLE 为空
     */
    private Integer parentGroupIndex;

    /**
     * 启动入参 JSON
     */
    private String inputParams;

    /**
     * 运行产出 JSON。全部节点完成时汇总
     */
    private String outputData;

    /**
     * 启动时间
     */
    private LocalDateTime startedAt;

    /**
     * 终态时间
     */
    private LocalDateTime finishedAt;

    /**
     * 失败码
     */
    private String errorCode;

    /**
     * 失败说明
     */
    private String errorMessage;

    /**
     * 乐观锁版本
     */
    private Integer rowVersion;

}

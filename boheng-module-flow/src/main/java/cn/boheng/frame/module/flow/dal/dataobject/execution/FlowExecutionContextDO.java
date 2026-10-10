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

/**
 * 分项执行上下文。INPUT 启动入参 / NODE 节点输出 / SYSTEM 系统变量，
 * 每个键一行——路由表达式、循环条件、参数映射都从这里取值。
 */
@TableName("flow_execution_context")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowExecutionContextDO extends TenantBaseDO {

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
     * INPUT / NODE / SYSTEM
     */
    private String namespace;

    /**
     * NODE 命名空间下的节点编码；其它命名空间为空
     */
    private String nodeId;

    /**
     * 循环轮次。NODE 命名空间下区分各轮输出
     */
    private Integer iterationNo;

    /**
     * 上下文键
     */
    private String contextKey;

    /**
     * 值 JSON
     */
    private String valueData;

}

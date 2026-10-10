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
 * route 节点的一次分支决策。决策器只算出 key，这里记下「选了哪条、
 * 激活了谁、当时的值是什么」——事后要能回答「这次为什么走了这条路」。
 */
@TableName("flow_branch_decision")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowBranchDecisionDO extends TenantBaseDO {

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
     * 路由节点编码
     */
    private String routeNodeId;

    /**
     * 节点的尝试序号
     */
    private Integer attempt;

    /**
     * 循环轮次
     */
    private Integer iterationNo;

    /**
     * 选中的出口名
     */
    private String selectedBranch;

    /**
     * 匹配语义：EXCLUSIVE / INCLUSIVE
     */
    private String matchMode;

    /**
     * 被激活的目标节点编码 JSON 数组
     */
    private String activatedTargets;

    /**
     * 决策快照 JSON：表达式文本 + 求值时的变量值
     */
    private String decisionData;

}

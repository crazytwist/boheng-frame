package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowBranchDecisionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 分支决策表。
 */
@Mapper
public interface FlowBranchDecisionMapper extends BaseMapperX<FlowBranchDecisionDO> {

    /**
     * 一个路由节点的全部决策。详情页展示「为什么走这条路」
     */
    default List<FlowBranchDecisionDO> selectByExecutionId(Long executionId) {
        return selectList(new LambdaQueryWrapperX<FlowBranchDecisionDO>()
                .eq(FlowBranchDecisionDO::getExecutionId, executionId)
                .orderByAsc(FlowBranchDecisionDO::getId));
    }

}

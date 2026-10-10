package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionContextDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 执行上下文表。
 */
@Mapper
public interface FlowExecutionContextMapper extends BaseMapperX<FlowExecutionContextDO> {

    /**
     * 一个运行的整棵上下文。内存里拼成 input/node/sys 三个 Map
     */
    default List<FlowExecutionContextDO> selectByExecutionId(Long executionId) {
        return selectList(new LambdaQueryWrapperX<FlowExecutionContextDO>()
                .eq(FlowExecutionContextDO::getExecutionId, executionId)
                .orderByAsc(FlowExecutionContextDO::getId));
    }

    /**
     * 删掉一个节点在某轮的输出（循环新一轮前重置）
     */
    default void deleteNodeOutput(Long executionId, String nodeId, Integer iterationNo) {
        delete(new LambdaQueryWrapperX<FlowExecutionContextDO>()
                .eq(FlowExecutionContextDO::getExecutionId, executionId)
                .eq(FlowExecutionContextDO::getNamespace, "NODE")
                .eq(FlowExecutionContextDO::getNodeId, nodeId)
                .eq(FlowExecutionContextDO::getIterationNo, iterationNo));
    }

}

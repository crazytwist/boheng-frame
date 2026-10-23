package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 节点执行表。
 */
@Mapper
public interface FlowStepExecutionMapper extends BaseMapperX<FlowStepExecutionDO> {

    /**
     * 一个运行的节点时间线。按节点顺序和尝试序号排
     */
    default List<FlowStepExecutionDO> selectByExecutionId(Long executionId) {
        return selectList(new LambdaQueryWrapperX<FlowStepExecutionDO>()
                .eq(FlowStepExecutionDO::getExecutionId, executionId)
                .orderByAsc(FlowStepExecutionDO::getId));
    }

    /**
     * 一个节点在某一轮的全部尝试。最新一次排最后
     */
    default List<FlowStepExecutionDO> selectAttempts(Long executionId, String nodeId, Integer iterationNo) {
        return selectList(new LambdaQueryWrapperX<FlowStepExecutionDO>()
                .eq(FlowStepExecutionDO::getExecutionId, executionId)
                .eq(FlowStepExecutionDO::getNodeId, nodeId)
                .eq(FlowStepExecutionDO::getIterationNo, iterationNo)
                .orderByAsc(FlowStepExecutionDO::getAttempt));
    }

    /**
     * 最新一次尝试。就绪判定和 CAS 认领都从这里出发
     */
    default FlowStepExecutionDO selectLatestAttempt(Long executionId, String nodeId, Integer iterationNo) {
        return selectOne(new LambdaQueryWrapperX<FlowStepExecutionDO>()
                .eq(FlowStepExecutionDO::getExecutionId, executionId)
                .eq(FlowStepExecutionDO::getNodeId, nodeId)
                .eq(FlowStepExecutionDO::getIterationNo, iterationNo)
                .orderByDesc(FlowStepExecutionDO::getAttempt)
                .last("LIMIT 1"));
    }

    /**
     * 步骤执行分页
     */
    default PageResult<FlowStepExecutionDO> selectPage(FlowStepExecutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowStepExecutionDO>()
                .eqIfPresent(FlowStepExecutionDO::getExecutionId, reqVO.getExecutionId())
                .eqIfPresent(FlowStepExecutionDO::getNodeId, reqVO.getNodeId())
                .eqIfPresent(FlowStepExecutionDO::getStatus, reqVO.getStatus())
                .orderByDesc(FlowStepExecutionDO::getId));
    }

    /**
     * 等待中的节点。超时扫描用
     */
    default List<FlowStepExecutionDO> selectWaiting(Long executionId) {
        return selectList(new LambdaQueryWrapperX<FlowStepExecutionDO>()
                .eq(FlowStepExecutionDO::getExecutionId, executionId)
                .eq(FlowStepExecutionDO::getStatus, "WAITING"));
    }

    /** 按设备命令定位等待中的流程节点，设备完成事件使用。 */
    default FlowStepExecutionDO selectWaitingByCommandId(Long commandId) {
        return selectOne(new LambdaQueryWrapperX<FlowStepExecutionDO>()
                .eq(FlowStepExecutionDO::getCommandId, commandId)
                .eq(FlowStepExecutionDO::getStatus, "WAITING")
                .last("LIMIT 1"));
    }

}

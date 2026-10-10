package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepSignalDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信号收件箱表。
 */
@Mapper
public interface FlowStepSignalMapper extends BaseMapperX<FlowStepSignalDO> {

    /**
     * 按 signal_id 查重。幂等消费的第一步
     */
    default FlowStepSignalDO selectBySignalId(String signalId) {
        return selectOne(FlowStepSignalDO::getSignalId, signalId);
    }

    /**
     * 还没消费的信号，按时间序取
     */
    default java.util.List<FlowStepSignalDO> selectPending() {
        return selectList(new LambdaQueryWrapperX<FlowStepSignalDO>()
                .eq(FlowStepSignalDO::getProcessStatus, "RECEIVED")
                .orderByAsc(FlowStepSignalDO::getId));
    }

}
